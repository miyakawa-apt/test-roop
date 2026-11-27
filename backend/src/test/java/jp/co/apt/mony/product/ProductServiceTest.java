package jp.co.apt.mony.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import jp.co.apt.mony.common.code.Category;
import jp.co.apt.mony.common.validation.ErrorType;
import jp.co.apt.mony.common.validation.ValidationFailedException;

/** DB なしのサービス単体テスト（リポジトリはモック）。 */
class ProductServiceTest {

	private ProductRepository repository;
	private ProductService service;

	@BeforeEach
	void setUp() {
		repository = mock(ProductRepository.class);
		service = new ProductService(repository);
		when(repository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private static ProductRequest request(String code) {
		return new ProductRequest(code, "みかん", "FOOD", "120", "24");
	}

	@Test
	void 一覧は商品コード順の取得結果を返す() {
		Product p = new Product("00000001", "みかん", Category.FOOD, 120, 24);
		when(repository.findAllByOrderByProductCodeAsc()).thenReturn(List.of(p));
		assertThat(service.list()).containsExactly(p);
	}

	@Test
	void 登録できる() {
		Product saved = service.register(request("00000001"));
		assertThat(saved.getProductCode()).isEqualTo("00000001");
		assertThat(saved.getProductName()).isEqualTo("みかん");
		assertThat(saved.getCategory()).isEqualTo(Category.FOOD);
		assertThat(saved.getUnitPrice()).isEqualTo(120);
		assertThat(saved.getCaseQuantity()).isEqualTo(24);
	}

	@Test
	void 商品コードが既にあれば重複() {
		when(repository.existsById("00000001")).thenReturn(true);
		assertThatThrownBy(() -> service.register(request("00000001")))
				.isInstanceOfSatisfying(ProductConflictException.class,
						e -> assertThat(e.reason()).isEqualTo(ProductConflictException.Reason.DUPLICATE));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 同時登録で一意制約に当たった場合も重複() {
		when(repository.saveAndFlush(any(Product.class))).thenThrow(new DataIntegrityViolationException("dup"));
		assertThatThrownBy(() -> service.register(request("00000001")))
				.isInstanceOfSatisfying(ProductConflictException.class,
						e -> assertThat(e.reason()).isEqualTo(ProductConflictException.Reason.DUPLICATE));
	}

	@Test
	void 検証エラーは複数まとめて返し_DBには触れない() {
		ProductRequest bad = new ProductRequest("ab", "", "NOPE", "-1", "0");
		assertThatThrownBy(() -> service.register(bad)).isInstanceOfSatisfying(ValidationFailedException.class, e -> {
			assertThat(e.result().errors()).hasSize(5);
			assertThat(e.result().typesOf("productCode")).containsExactly(ErrorType.INVALID_FORMAT);
			assertThat(e.result().typesOf("productName")).containsExactly(ErrorType.REQUIRED);
			assertThat(e.result().typesOf("category")).containsExactly(ErrorType.INVALID_FORMAT);
			assertThat(e.result().typesOf("unitPrice")).containsExactly(ErrorType.INVALID_FORMAT);
			assertThat(e.result().typesOf("caseQuantity")).containsExactly(ErrorType.OUT_OF_RANGE);
		});
		verify(repository, never()).existsById(any());
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 本文なしは全項目が未入力() {
		assertThatThrownBy(() -> service.register(null)).isInstanceOfSatisfying(ValidationFailedException.class,
				e -> assertThat(e.result().errors()).hasSize(5)
						.allSatisfy(err -> assertThat(err.type()).isEqualTo(ErrorType.REQUIRED)));
	}

	@Test
	void 商品名は30文字まで_31文字は文字数エラー() {
		assertThat(service.register(new ProductRequest("00000001", "あ".repeat(30), "TOOL", "0", "1")))
				.isNotNull();
		assertThatThrownBy(() -> service.register(new ProductRequest("00000002", "あ".repeat(31), "TOOL", "0", "1")))
				.isInstanceOfSatisfying(ValidationFailedException.class,
						e -> assertThat(e.result().typesOf("productName")).containsExactly(ErrorType.INVALID_LENGTH));
	}

	@Test
	void 更新は商品コード以外を書き換える() {
		Product existing = new Product("00000001", "みかん", Category.FOOD, 120, 24);
		when(repository.findById("00000001")).thenReturn(Optional.of(existing));

		Product updated = service.update("00000001", new ProductRequest("99999999", "ねじ", "PARTS", "5", "100"));

		assertThat(updated.getProductCode()).isEqualTo("00000001");
		assertThat(updated.getProductName()).isEqualTo("ねじ");
		assertThat(updated.getCategory()).isEqualTo(Category.PARTS);
		assertThat(updated.getUnitPrice()).isEqualTo(5);
		assertThat(updated.getCaseQuantity()).isEqualTo(100);
	}

	@Test
	void 更新で対象がなければ存在しない() {
		when(repository.findById("00000001")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.update("00000001", request(null)))
				.isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void 更新の検証エラーは対象の有無より先に返す() {
		assertThatThrownBy(() -> service.update("00000001", new ProductRequest(null, "", "FOOD", "1", "1")))
				.isInstanceOf(ValidationFailedException.class);
		verify(repository, never()).findById(any());
	}

	@Test
	void 削除できる() {
		Product existing = new Product("00000001", "みかん", Category.FOOD, 120, 24);
		when(repository.findById("00000001")).thenReturn(Optional.of(existing));
		when(repository.countStockRows("00000001")).thenReturn(0L);

		service.delete("00000001");

		verify(repository).delete(existing);
	}

	@Test
	void 在庫がある商品は削除できない() {
		Product existing = new Product("00000001", "みかん", Category.FOOD, 120, 24);
		when(repository.findById("00000001")).thenReturn(Optional.of(existing));
		when(repository.countStockRows("00000001")).thenReturn(2L);

		assertThatThrownBy(() -> service.delete("00000001")).isInstanceOfSatisfying(ProductConflictException.class,
				e -> assertThat(e.reason()).isEqualTo(ProductConflictException.Reason.HAS_STOCK));
		verify(repository, never()).delete(any());
	}

	@Test
	void 削除で対象がなければ存在しない() {
		when(repository.findById("00000001")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.delete("00000001")).isInstanceOf(ProductNotFoundException.class);
	}

	@Test
	void 登録更新削除はトランザクション境界を持つ() throws Exception {
		for (String name : List.of("register", "update", "delete")) {
			var method = java.util.Arrays.stream(ProductService.class.getDeclaredMethods())
					.filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
			assertThat(method.getAnnotation(Transactional.class)).as(name).isNotNull();
		}
	}

}
