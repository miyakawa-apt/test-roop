package jp.co.apt.mony.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 実DBで初期管理者の登録を確かめる。各テストはロールバックされ、データを残さない
 * （テスト内で app_user を空にしても、終了時に元へ戻る）。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_PASSWORD", matches = ".+")
class AdminBootstrapIntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	private final String secret = "Tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);

	private void emptyUsers() {
		jdbc.update("DELETE FROM work_result");
		jdbc.update("DELETE FROM app_user");
	}

	@Test
	void ユーザーが0件のときだけ管理者A001が登録される() {
		emptyUsers();

		new AdminBootstrap(jdbc, encoder, secret).run(null);

		Map<String, Object> row = jdbc.queryForMap("SELECT * FROM app_user");
		assertThat(row.get("user_id")).isEqualTo("A001");
		assertThat(row.get("user_name")).isEqualTo("管理者");
		assertThat(row.get("role")).isEqualTo("管理者");
		String hash = (String) row.get("password_hash");
		assertThat(hash).isNotEqualTo(secret);
		assertThat(encoder.matches(secret, hash)).isTrue();
	}

	@Test
	void 既にユーザーがいれば登録しない() {
		emptyUsers();
		jdbc.update("INSERT INTO app_user VALUES ('T003', '既存', ?, '一般')", encoder.encode(secret));

		new AdminBootstrap(jdbc, encoder, secret).run(null);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE user_id = 'A001'", Integer.class)).isZero();
	}

	@Test
	void 環境変数が空なら0件でも登録しない() {
		emptyUsers();

		new AdminBootstrap(jdbc, encoder, "").run(null);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class)).isZero();
	}

}
