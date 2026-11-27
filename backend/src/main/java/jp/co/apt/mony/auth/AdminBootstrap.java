package jp.co.apt.mony.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.common.validation.InputRule;

/**
 * 最初の管理者の登録。環境変数 MONY_BOOTSTRAP_ADMIN_PASSWORD が設定され、app_user が 0 件のときだけ
 * 起動時にユーザーID A001・氏名「管理者」・権限 管理者 を登録する。パスワードはログに出さない。
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

	static final String ADMIN_USER_ID = "A001";
	static final String ADMIN_USER_NAME = "管理者";

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final JdbcTemplate jdbcTemplate;
	private final PasswordEncoder passwordEncoder;
	private final String adminPassword;

	public AdminBootstrap(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder,
			@Value("${MONY_BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword) {
		this.jdbcTemplate = jdbcTemplate;
		this.passwordEncoder = passwordEncoder;
		this.adminPassword = adminPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (adminPassword == null || adminPassword.isEmpty()) {
			return;
		}
		Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM app_user", Integer.class);
		if (count == null || count > 0) {
			return;
		}
		if (InputRule.PASSWORD.check(adminPassword).isPresent()) {
			log.warn("MONY_BOOTSTRAP_ADMIN_PASSWORD がパスワードの入力ルール（8〜14文字）に合わないため、初期管理者を登録しません");
			return;
		}
		try {
			jdbcTemplate.update("INSERT INTO app_user (user_id, user_name, password_hash, role) VALUES (?, ?, ?, ?)",
					ADMIN_USER_ID, ADMIN_USER_NAME, passwordEncoder.encode(adminPassword), Role.ADMIN.label());
			log.info("初期管理者を登録しました（ユーザーID: {}）", ADMIN_USER_ID);
		} catch (DuplicateKeyException e) {
			// 同時起動で先に登録された場合は何もしない
		}
	}

}
