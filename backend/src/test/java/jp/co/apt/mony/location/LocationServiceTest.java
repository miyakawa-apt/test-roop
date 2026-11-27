package jp.co.apt.mony.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import jp.co.apt.mony.common.code.ShelfStatus;
import jp.co.apt.mony.common.validation.ErrorType;
import jp.co.apt.mony.common.validation.ValidationFailedException;

/** DB なしのサービス単体テスト（リポジトリはモック）。 */
class LocationServiceTest {

	private LocationRepository repository;
	private LocationService service;

	@BeforeEach
	void setUp() {
		repository = mock(LocationRepository.class);
		service = new LocationService(repository);
		when(repository.saveAndFlush(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private static LocationRequest request(String shelfNo, String status, String remarks) {
		return new LocationRequest(shelfNo, status, remarks);
	}

	private static void assertConflict(Runnable action, LocationConflictException.Reason reason) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(LocationConflictException.class,
				e -> assertThat(e.reason()).isEqualTo(reason));
	}

	@Test
	void 一覧は棚番順の取得結果を返す() {
		Location l = new Location("01-001-01", ShelfStatus.EMPTY, null);
		when(repository.findAllByOrderByShelfNoAsc()).thenReturn(List.of(l));
		assertThat(service.list()).containsExactly(l);
	}

	@Test
	void 登録できる_備考が空ならnullで保存する() {
		Location saved = service.register(request("01-001-01", "EMPTY", ""));
		assertThat(saved.getShelfNo()).isEqualTo("01-001-01");
		assertThat(saved.getShelfStatus()).isEqualTo(ShelfStatus.EMPTY);
		assertThat(saved.getRemarks()).isNull();
		assertThat(service.register(request("01-001-02", "PROHIBITED", "点検中")).getRemarks()).isEqualTo("点検中");
	}

	@Test
	void 棚番が既にあれば重複() {
		when(repository.existsById("01-001-01")).thenReturn(true);
		assertConflict(() -> service.register(request("01-001-01", "EMPTY", "")),
				LocationConflictException.Reason.DUPLICATE);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 同時登録で一意制約に当たった場合も重複() {
		when(repository.saveAndFlush(any(Location.class))).thenThrow(new DataIntegrityViolationException("dup"));
		assertConflict(() -> service.register(request("01-001-01", "EMPTY", "")),
				LocationConflictException.Reason.DUPLICATE);
	}

	@Test
	void 登録の検証エラーは複数まとめて返し_DBには触れない() {
		LocationRequest bad = request("1-1-1", "", "あ".repeat(51));
		assertThatThrownBy(() -> service.register(bad)).isInstanceOfSatisfying(ValidationFailedException.class, e -> {
			assertThat(e.result().errors()).hasSize(3);
			assertThat(e.result().typesOf("shelfNo")).containsExactly(ErrorType.INVALID_FORMAT);
			assertThat(e.result().typesOf("shelfStatus")).containsExactly(ErrorType.REQUIRED);
			assertThat(e.result().typesOf("remarks")).containsExactly(ErrorType.INVALID_LENGTH);
		});
		verify(repository, never()).existsById(any());
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 本文なしは棚番と棚状況が未入力_備考は任意() {
		assertThatThrownBy(() -> service.register(null)).isInstanceOfSatisfying(ValidationFailedException.class,
				e -> assertThat(e.result().errors()).hasSize(2)
						.allSatisfy(err -> assertThat(err.type()).isEqualTo(ErrorType.REQUIRED)));
	}

	@Test
	void 登録で選べる棚状況は空棚と禁止棚だけ_実棚と不正値は形式エラー() {
		for (String status : List.of("STOCKED", "NOPE", "空棚")) {
			assertThatThrownBy(() -> service.register(request("01-001-01", status, "")))
					.isInstanceOfSatisfying(ValidationFailedException.class,
							e -> assertThat(e.result().typesOf("shelfStatus")).containsExactly(ErrorType.INVALID_FORMAT));
		}
	}

	@Test
	void 備考は50文字まで() {
		assertThat(service.register(request("01-001-01", "EMPTY", "あ".repeat(50)))).isNotNull();
		assertThatThrownBy(() -> service.register(request("01-001-02", "EMPTY", "あ".repeat(51))))
				.isInstanceOf(ValidationFailedException.class);
	}

	@Test
	void 備考と棚状況を更新できる() {
		Location existing = new Location("01-001-01", ShelfStatus.EMPTY, null);
		when(repository.findById("01-001-01")).thenReturn(Optional.of(existing));

		Location updated = service.update("01-001-01", request("01-001-01", "PROHIBITED", "点検中"));

		assertThat(updated.getShelfNo()).isEqualTo("01-001-01");
		assertThat(updated.getShelfStatus()).isEqualTo(ShelfStatus.PROHIBITED);
		assertThat(updated.getRemarks()).isEqualTo("点検中");
		verify(repository, never()).renumber(anyString(), anyString(), anyString(), any());
	}

	@Test
	void 更新で対象がなければ存在しない() {
		when(repository.findById("01-001-01")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.update("01-001-01", request("01-001-01", "EMPTY", "")))
				.isInstanceOf(LocationNotFoundException.class);
	}

	@Test
	void 更新の検証エラーは対象の有無より先に返す() {
		assertThatThrownBy(() -> service.update("01-001-01", request("", "EMPTY", "")))
				.isInstanceOf(ValidationFailedException.class);
		verify(repository, never()).findById(any());
	}

	@Test
	void 棚番を変更できる_在庫がなく新棚番が未使用なら主キーを更新する() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.renumber("01-001-01", "02-002-02", "禁止棚", "移設")).thenReturn(1);

		Location updated = service.update("01-001-01", request("02-002-02", "PROHIBITED", "移設"));

		assertThat(updated.getShelfNo()).isEqualTo("02-002-02");
		assertThat(updated.getShelfStatus()).isEqualTo(ShelfStatus.PROHIBITED);
		assertThat(updated.getRemarks()).isEqualTo("移設");
		verify(repository).renumber("01-001-01", "02-002-02", "禁止棚", "移設");
	}

	@Test
	void 棚番変更で新棚番が既にあれば重複() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.existsById("02-002-02")).thenReturn(true);
		assertConflict(() -> service.update("01-001-01", request("02-002-02", "EMPTY", "")),
				LocationConflictException.Reason.DUPLICATE);
		verify(repository, never()).renumber(anyString(), anyString(), anyString(), any());
	}

	@Test
	void 棚番変更で同時に新棚番を使われ一意制約に当たった場合も重複() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.renumber(anyString(), anyString(), anyString(), any()))
				.thenThrow(new DataIntegrityViolationException("duplicate key value violates pk_location"));
		assertConflict(() -> service.update("01-001-01", request("02-002-02", "EMPTY", "")),
				LocationConflictException.Reason.DUPLICATE);
	}

	@Test
	void 棚番変更の直後に入庫され外部キーに当たった場合は在庫あり() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.renumber(anyString(), anyString(), anyString(), any()))
				.thenThrow(new DataIntegrityViolationException("violates foreign key constraint fk_stock_location"));
		assertConflict(() -> service.update("01-001-01", request("02-002-02", "EMPTY", "")),
				LocationConflictException.Reason.HAS_STOCK);
	}

	@Test
	void 在庫のある棚は棚番を変更できない() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.countStockRows("01-001-01")).thenReturn(1L);
		assertConflict(() -> service.update("01-001-01", request("02-002-02", "EMPTY", "")),
				LocationConflictException.Reason.HAS_STOCK);
		verify(repository, never()).renumber(anyString(), anyString(), anyString(), any());
	}

	@Test
	void 実棚は棚番を変更できないが_備考だけなら更新できる() {
		Location stocked = new Location("01-001-01", ShelfStatus.STOCKED, null);
		when(repository.findById("01-001-01")).thenReturn(Optional.of(stocked));
		when(repository.countStockRows("01-001-01")).thenReturn(1L);

		assertConflict(() -> service.update("01-001-01", request("02-002-02", "STOCKED", "")),
				LocationConflictException.Reason.HAS_STOCK);

		Location updated = service.update("01-001-01", request("01-001-01", "STOCKED", "上段"));
		assertThat(updated.getShelfStatus()).isEqualTo(ShelfStatus.STOCKED);
		assertThat(updated.getRemarks()).isEqualTo("上段");
	}

	@Test
	void 在庫のある棚は禁止棚にも空棚にも更新できない() {
		Location stocked = new Location("01-001-01", ShelfStatus.STOCKED, null);
		when(repository.findById("01-001-01")).thenReturn(Optional.of(stocked));
		when(repository.countStockRows("01-001-01")).thenReturn(3L);
		for (String status : List.of("PROHIBITED", "EMPTY")) {
			assertConflict(() -> service.update("01-001-01", request("01-001-01", status, "")),
					LocationConflictException.Reason.HAS_STOCK);
		}
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 実棚でない棚を実棚へは更新できない() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		assertThatThrownBy(() -> service.update("01-001-01", request("01-001-01", "STOCKED", "")))
				.isInstanceOfSatisfying(ValidationFailedException.class,
						e -> assertThat(e.result().typesOf("shelfStatus")).containsExactly(ErrorType.INVALID_FORMAT));
	}

	@Test
	void 棚番変更の更新行がなければ存在しない() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.renumber(anyString(), anyString(), anyString(), any())).thenReturn(0);
		assertThatThrownBy(() -> service.update("01-001-01", request("02-002-02", "EMPTY", "")))
				.isInstanceOf(LocationNotFoundException.class);
	}

	@Test
	void 更新の途中で同時に削除された楽観ロック例外は存在しないに変換する() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.saveAndFlush(any(Location.class)))
				.thenThrow(new ObjectOptimisticLockingFailureException(Location.class, "01-001-01"));
		assertThatThrownBy(() -> service.update("01-001-01", request("01-001-01", "EMPTY", "x")))
				.isInstanceOf(LocationNotFoundException.class);
	}

	@Test
	void 更新の途中のStaleState系例外も存在しないに変換する() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.saveAndFlush(any(Location.class)))
				.thenThrow(new org.hibernate.StaleStateException("Batch update returned unexpected row count"));
		assertThatThrownBy(() -> service.update("01-001-01", request("01-001-01", "EMPTY", "x")))
				.isInstanceOf(LocationNotFoundException.class);
	}

	@Test
	void 削除できる() {
		Location existing = new Location("01-001-01", ShelfStatus.PROHIBITED, null);
		when(repository.findById("01-001-01")).thenReturn(Optional.of(existing));
		service.delete("01-001-01");
		verify(repository).delete(existing);
	}

	@Test
	void 在庫がある棚は削除できない() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.countStockRows("01-001-01")).thenReturn(2L);
		assertConflict(() -> service.delete("01-001-01"), LocationConflictException.Reason.HAS_STOCK);
		verify(repository, never()).delete(any());
	}

	@Test
	void 実棚は削除できない() {
		when(repository.findById("01-001-01"))
				.thenReturn(Optional.of(new Location("01-001-01", ShelfStatus.STOCKED, null)));
		assertConflict(() -> service.delete("01-001-01"), LocationConflictException.Reason.HAS_STOCK);
		verify(repository, never()).delete(any());
	}

	@Test
	void 削除の直後に入庫され外部キーに当たった場合も在庫あり() {
		Location existing = new Location("01-001-01", ShelfStatus.EMPTY, null);
		when(repository.findById("01-001-01")).thenReturn(Optional.of(existing));
		doThrow(new DataIntegrityViolationException("fk")).when(repository).flush();
		assertConflict(() -> service.delete("01-001-01"), LocationConflictException.Reason.HAS_STOCK);
	}

	@Test
	void 削除で対象がなければ存在しない() {
		when(repository.findById("01-001-01")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.delete("01-001-01")).isInstanceOf(LocationNotFoundException.class);
	}

	@Test
	void 削除の途中で同時に削除された楽観ロック例外は存在しないに変換する() {
		Location existing = new Location("01-001-01", ShelfStatus.EMPTY, null);
		when(repository.findById("01-001-01")).thenReturn(Optional.of(existing));
		doThrow(new ObjectOptimisticLockingFailureException(Location.class, "01-001-01")).when(repository).flush();
		assertThatThrownBy(() -> service.delete("01-001-01")).isInstanceOf(LocationNotFoundException.class);

		doThrow(new org.hibernate.StaleStateException("stale")).when(repository).flush();
		assertThatThrownBy(() -> service.delete("01-001-01")).isInstanceOf(LocationNotFoundException.class);
	}

	@Test
	void 登録更新削除はトランザクション境界を持つ() {
		for (String name : List.of("register", "update", "delete")) {
			var method = java.util.Arrays.stream(LocationService.class.getDeclaredMethods())
					.filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
			assertThat(method.getAnnotation(Transactional.class)).as(name).isNotNull();
		}
	}

}
