package jp.co.apt.mony.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** DB なしの単体テスト（JdbcTemplate はモック）。 */
class AdminBootstrapTest {

	private static final String INSERT = "INSERT INTO app_user (user_id, user_name, password_hash, role) VALUES (?, ?, ?, ?)";

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final PasswordEncoder encoder = new BCryptPasswordEncoder();
	private final String secret = "Tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

	@Test
	void 環境変数が空なら何もしない() {
		new AdminBootstrap(jdbc, encoder, "").run(null);
		verify(jdbc, never()).update(anyString(), any(Object[].class));
		verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class));
	}

	@Test
	void ユーザーが0件なら管理者A001をハッシュで登録する() {
		when(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).thenReturn(0);

		new AdminBootstrap(jdbc, encoder, secret).run(null);

		ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
		verify(jdbc).update(eq(INSERT), args.capture(), args.capture(), args.capture(), args.capture());
		assertThat(args.getAllValues().get(0)).isEqualTo("A001");
		assertThat(args.getAllValues().get(1)).isEqualTo("管理者");
		String hash = (String) args.getAllValues().get(2);
		assertThat(hash).isNotEqualTo(secret);
		assertThat(encoder.matches(secret, hash)).isTrue();
		assertThat(args.getAllValues().get(3)).isEqualTo("管理者");
	}

	@Test
	void ユーザーが既にいれば何もしない() {
		when(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).thenReturn(1);

		new AdminBootstrap(jdbc, encoder, secret).run(null);

		verify(jdbc, never()).update(anyString(), any(Object[].class));
	}

	@Test
	void 入力ルールに合わない値なら登録しない() {
		when(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).thenReturn(0);

		new AdminBootstrap(jdbc, encoder, "short").run(null);

		verify(jdbc, never()).update(anyString(), any(Object[].class));
	}

}
