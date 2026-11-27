package jp.co.apt.mony.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.common.validation.ErrorType;
import jp.co.apt.mony.common.validation.ValidationFailedException;

/** DB なしのサービス単体テスト（リポジトリはモック）。 */
class UserServiceTest {

	/** テストごとに生成する一時値（ソースに固定値を書かない）。 */
	private static String temporaryValue() {
		return "Tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
	private UserRepository repository;
	private UserService service;

	@BeforeEach
	void setUp() {
		repository = mock(UserRepository.class);
		service = new UserService(repository, encoder);
		when(repository.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private static UserRequest request(String userId, String rawPw) {
		return new UserRequest(userId, "山田太郎", rawPw, "GENERAL");
	}

	private UserAccount existing(String userId, Role role) {
		return new UserAccount(userId, "元の名前", encoder.encode(temporaryValue()), role);
	}

	private void admins(int count) {
		when(repository.lockAllByRole(Role.ADMIN)).thenReturn(
				java.util.stream.IntStream.range(0, count).mapToObj(i -> existing("A00" + i, Role.ADMIN)).toList());
	}

	@Test
	void 一覧はユーザーID順の取得結果を返す() {
		UserAccount u = existing("A001", Role.ADMIN);
		when(repository.findAllByOrderByUserIdAsc()).thenReturn(List.of(u));
		assertThat(service.list()).containsExactly(u);
	}

	@Test
	void 登録できる_パスワードは平文でなくハッシュで保存される() {
		String rawPw = temporaryValue();
		UserAccount saved = service.register(request("U001", rawPw));
		assertThat(saved.getUserId()).isEqualTo("U001");
		assertThat(saved.getUserName()).isEqualTo("山田太郎");
		assertThat(saved.getRole()).isEqualTo(Role.GENERAL);
		assertThat(saved.getPasswordHash()).isNotEqualTo(rawPw).doesNotContain(rawPw);
		assertThat(encoder.matches(rawPw, saved.getPasswordHash())).isTrue();
	}

	@Test
	void ユーザーIDが既にあれば重複() {
		when(repository.existsById("U001")).thenReturn(true);
		assertThatThrownBy(() -> service.register(request("U001", temporaryValue())))
				.isInstanceOfSatisfying(UserConflictException.class,
						e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.DUPLICATE));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 同時登録で一意制約に当たった場合も重複() {
		when(repository.saveAndFlush(any(UserAccount.class))).thenThrow(new DataIntegrityViolationException("dup"));
		assertThatThrownBy(() -> service.register(request("U001", temporaryValue())))
				.isInstanceOfSatisfying(UserConflictException.class,
						e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.DUPLICATE));
	}

	@Test
	void 登録の検証エラーは複数まとめて返し_DBには触れない() {
		UserRequest bad = new UserRequest("ab", "", "short", "NOPE");
		assertThatThrownBy(() -> service.register(bad)).isInstanceOfSatisfying(ValidationFailedException.class, e -> {
			assertThat(e.result().errors()).hasSize(4);
			assertThat(e.result().typesOf("userId")).containsExactly(ErrorType.INVALID_FORMAT);
			assertThat(e.result().typesOf("userName")).containsExactly(ErrorType.REQUIRED);
			assertThat(e.result().typesOf("password")).containsExactly(ErrorType.INVALID_LENGTH);
			assertThat(e.result().typesOf("role")).containsExactly(ErrorType.INVALID_FORMAT);
		});
		verify(repository, never()).existsById(any());
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 本文なしの登録は全項目が未入力() {
		assertThatThrownBy(() -> service.register(null)).isInstanceOfSatisfying(ValidationFailedException.class,
				e -> assertThat(e.result().errors()).hasSize(4)
						.allSatisfy(err -> assertThat(err.type()).isEqualTo(ErrorType.REQUIRED)));
	}

	@Test
	void 登録ではパスワードが必須_空は未入力エラー() {
		assertThatThrownBy(() -> service.register(request("U001", "")))
				.isInstanceOfSatisfying(ValidationFailedException.class,
						e -> assertThat(e.result().typesOf("password")).containsExactly(ErrorType.REQUIRED));
	}

	@Test
	void パスワードは8文字から14文字まで() {
		assertThat(service.register(request("U001", "a".repeat(8)))).isNotNull();
		assertThat(service.register(request("U002", "a".repeat(14)))).isNotNull();
		assertThatThrownBy(() -> service.register(request("U003", "a".repeat(7))))
				.isInstanceOf(ValidationFailedException.class);
		assertThatThrownBy(() -> service.register(request("U004", "a".repeat(15))))
				.isInstanceOf(ValidationFailedException.class);
	}

	@Test
	void 更新は氏名と権限を書き換え_ユーザーIDは変わらない() {
		UserAccount u = existing("U001", Role.GENERAL);
		String before = u.getPasswordHash();
		when(repository.findById("U001")).thenReturn(Optional.of(u));

		UserAccount updated = service.update("U001", new UserRequest("ZZZZ", "新しい名前", null, "ADMIN"));

		assertThat(updated.getUserId()).isEqualTo("U001");
		assertThat(updated.getUserName()).isEqualTo("新しい名前");
		assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
		assertThat(updated.getPasswordHash()).isEqualTo(before); // パスワード欄なし→変更しない
	}

	@Test
	void 更新でパスワード欄が空文字でもパスワードは変更しない() {
		UserAccount u = existing("U001", Role.GENERAL);
		String before = u.getPasswordHash();
		when(repository.findById("U001")).thenReturn(Optional.of(u));
		service.update("U001", new UserRequest(null, "名前", "", "GENERAL"));
		assertThat(u.getPasswordHash()).isEqualTo(before);
	}

	@Test
	void 更新でパスワードが入っていればハッシュを更新する() {
		UserAccount u = existing("U001", Role.GENERAL);
		String before = u.getPasswordHash();
		String rawPw = temporaryValue();
		when(repository.findById("U001")).thenReturn(Optional.of(u));

		service.update("U001", new UserRequest(null, "名前", rawPw, "GENERAL"));

		assertThat(u.getPasswordHash()).isNotEqualTo(before).isNotEqualTo(rawPw);
		assertThat(encoder.matches(rawPw, u.getPasswordHash())).isTrue();
	}

	@Test
	void 更新で入力されたパスワードが規則外なら検証エラー() {
		assertThatThrownBy(() -> service.update("U001", new UserRequest(null, "名前", "short", "GENERAL")))
				.isInstanceOfSatisfying(ValidationFailedException.class,
						e -> assertThat(e.result().typesOf("password")).containsExactly(ErrorType.INVALID_LENGTH));
		verify(repository, never()).findById(any());
	}

	@Test
	void 更新の検証エラーは複数まとめて返し_対象の有無より先に返す() {
		assertThatThrownBy(() -> service.update("U001", new UserRequest(null, "あ".repeat(16), null, "")))
				.isInstanceOfSatisfying(ValidationFailedException.class, e -> {
					assertThat(e.result().errors()).hasSize(2);
					assertThat(e.result().typesOf("userName")).containsExactly(ErrorType.INVALID_LENGTH);
					assertThat(e.result().typesOf("role")).containsExactly(ErrorType.REQUIRED);
				});
		verify(repository, never()).findById(any());
	}

	@Test
	void 更新で対象がなければ存在しない() {
		when(repository.findById("U001")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.update("U001", request(null, null)))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void 最後の管理者を一般へ変更することはできない() {
		UserAccount admin = existing("A001", Role.ADMIN);
		when(repository.findById("A001")).thenReturn(Optional.of(admin));
		admins(1);
		assertThatThrownBy(() -> service.update("A001", new UserRequest(null, "名前", null, "GENERAL")))
				.isInstanceOfSatisfying(UserConflictException.class,
						e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.LAST_ADMIN));
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 管理者が複数いれば一般へ変更できる_管理者のまま氏名変更は常にできる() {
		UserAccount admin = existing("A001", Role.ADMIN);
		when(repository.findById("A001")).thenReturn(Optional.of(admin));
		admins(2);
		assertThat(service.update("A001", new UserRequest(null, "名前", null, "GENERAL")).getRole())
				.isEqualTo(Role.GENERAL);

		UserAccount only = existing("A002", Role.ADMIN);
		when(repository.findById("A002")).thenReturn(Optional.of(only));
		admins(1);
		assertThat(service.update("A002", new UserRequest(null, "別名", null, "ADMIN")).getUserName())
				.isEqualTo("別名");
	}

	@Test
	void 更新の途中で同時に削除された場合は存在しない() {
		when(repository.findById("U001")).thenReturn(Optional.of(existing("U001", Role.GENERAL)));
		when(repository.saveAndFlush(any(UserAccount.class)))
				.thenThrow(new ObjectOptimisticLockingFailureException(UserAccount.class, "U001"));
		assertThatThrownBy(() -> service.update("U001", request(null, null)))
				.isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void 削除できる() {
		UserAccount u = existing("U001", Role.GENERAL);
		when(repository.findById("U001")).thenReturn(Optional.of(u));
		when(repository.countResultRows("U001")).thenReturn(0L);

		service.delete("U001", "A001");

		verify(repository).delete(u);
	}

	@Test
	void ログイン中の本人は削除できない() {
		when(repository.findById("A001")).thenReturn(Optional.of(existing("A001", Role.ADMIN)));
		admins(2);
		assertThatThrownBy(() -> service.delete("A001", "A001")).isInstanceOfSatisfying(UserConflictException.class,
				e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.SELF));
		verify(repository, never()).delete(any());
	}

	@Test
	void 最後の管理者は削除できない() {
		when(repository.findById("A002")).thenReturn(Optional.of(existing("A002", Role.ADMIN)));
		admins(1);
		assertThatThrownBy(() -> service.delete("A002", "A001")).isInstanceOfSatisfying(UserConflictException.class,
				e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.LAST_ADMIN));
		verify(repository, never()).delete(any());
	}

	@Test
	void 管理者が複数いれば他の管理者は削除できる() {
		UserAccount other = existing("A002", Role.ADMIN);
		when(repository.findById("A002")).thenReturn(Optional.of(other));
		admins(2);
		service.delete("A002", "A001");
		verify(repository).delete(other);
	}

	@Test
	void 実績の履歴があるユーザーは削除できない() {
		when(repository.findById("U001")).thenReturn(Optional.of(existing("U001", Role.GENERAL)));
		when(repository.countResultRows("U001")).thenReturn(3L);
		assertThatThrownBy(() -> service.delete("U001", "A001")).isInstanceOfSatisfying(UserConflictException.class,
				e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.HAS_RESULTS));
		verify(repository, never()).delete(any());
	}

	@Test
	void 確認の直後に実績ができて外部キー違反になっても_実績ありの409() {
		when(repository.findById("U001")).thenReturn(Optional.of(existing("U001", Role.GENERAL)));
		doThrow(new DataIntegrityViolationException("fk")).when(repository).flush();
		assertThatThrownBy(() -> service.delete("U001", "A001")).isInstanceOfSatisfying(UserConflictException.class,
				e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.HAS_RESULTS));
	}

	@Test
	void 削除で対象がなければ存在しない() {
		when(repository.findById("U001")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.delete("U001", "A001")).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void 削除の途中で同時に削除された場合_楽観ロック系の例外は存在しないに変換する() {
		when(repository.findById("U001")).thenReturn(Optional.of(existing("U001", Role.GENERAL)));
		doThrow(new ObjectOptimisticLockingFailureException(UserAccount.class, "U001")).when(repository).flush();
		assertThatThrownBy(() -> service.delete("U001", "A001")).isInstanceOf(UserNotFoundException.class);
	}

	@Test
	void 登録更新削除はトランザクション境界を持つ() {
		for (String name : List.of("register", "update", "delete")) {
			var method = java.util.Arrays.stream(UserService.class.getDeclaredMethods())
					.filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
			assertThat(method.getAnnotation(Transactional.class)).as(name).isNotNull();
		}
	}

	@Test
	void 要求の文字列表現にパスワードを含めない() {
		String rawPw = temporaryValue();
		assertThat(new UserRequest("U001", "山田", rawPw, "GENERAL").toString()).doesNotContain(rawPw);
	}

}
