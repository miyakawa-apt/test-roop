package jp.co.apt.mony.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;


import jakarta.servlet.http.Cookie;
import jp.co.apt.mony.common.code.Role;

/**
 * 実DB（PostgreSQL）でログイン・権限制御・セッションのDB保存を確かめる。
 * 環境変数 SPRING_DATASOURCE_PASSWORD があるときだけ実行する。テスト用ユーザー（T001・T002）と
 * そのセッションは各テストの後に削除する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_PASSWORD", matches = ".+")
class AuthIntegrationTest {

	private static final String ADMIN_ID = "T001";
	private static final String GENERAL_ID = "T002";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	private final String adminSecret = temporaryValue();
	private final String generalSecret = temporaryValue();

	private static String temporaryValue() {
		return "Tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	@BeforeEach
	void insertUsers() {
		cleanUp();
		jdbc.update("INSERT INTO app_user VALUES (?, ?, ?, ?)", ADMIN_ID, "試験管理者", encoder.encode(adminSecret),
				Role.ADMIN.label());
		jdbc.update("INSERT INTO app_user VALUES (?, ?, ?, ?)", GENERAL_ID, "試験一般", encoder.encode(generalSecret),
				Role.GENERAL.label());
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("DELETE FROM spring_session WHERE principal_name IN (?, ?)", ADMIN_ID, GENERAL_ID);
		jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", ADMIN_ID, GENERAL_ID);
	}

	private static String loginJson(String userId, String secret) {
		return "{\"userId\":\"" + userId + "\",\"password\":\"" + secret + "\"}";
	}

	private Cookie login(String userId, String secret) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson(userId, secret)))
				.andExpect(status().isOk()).andReturn();
		Cookie session = result.getResponse().getCookie("SESSION");
		assertThat(session).as("Spring Session の SESSION Cookie").isNotNull();
		return session;
	}

	private int sessionCount(String userId) {
		return jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class,
				userId);
	}

	@Test
	void DBのユーザーでログインでき_BCryptハッシュで保存されている() throws Exception {
		String stored = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE user_id = ?", String.class,
				ADMIN_ID);
		assertThat(stored).startsWith("$2").isNotEqualTo(adminSecret);

		Cookie session = login(ADMIN_ID, adminSecret);
		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(ADMIN_ID))
				.andExpect(jsonPath("$.userName").value("試験管理者"))
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void パスワード不一致と存在しないIDは同じ401() throws Exception {
		String other = temporaryValue();
		MvcResult wrong = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson(ADMIN_ID, other)))
				.andExpect(status().isUnauthorized()).andReturn();
		MvcResult unknown = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson("Z999", other)))
				.andExpect(status().isUnauthorized()).andReturn();
		assertThat(unknown.getResponse().getContentAsString()).isEqualTo(wrong.getResponse().getContentAsString());
		assertThat(sessionCount(ADMIN_ID)).isZero();
	}

	@Test
	void ログイン状態がDBのspring_sessionに保存される() throws Exception {
		assertThat(sessionCount(GENERAL_ID)).isZero();
		login(GENERAL_ID, generalSecret);
		assertThat(sessionCount(GENERAL_ID)).isEqualTo(1);
	}

	@Test
	void ログアウトするとmeが401になりセッションもDBから消える() throws Exception {
		Cookie session = login(ADMIN_ID, adminSecret);
		assertThat(sessionCount(ADMIN_ID)).isEqualTo(1);

		mockMvc.perform(post("/api/auth/logout").with(CsrfSupport.withCsrf()).cookie(session)).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").cookie(session)).andExpect(status().isUnauthorized());
		assertThat(sessionCount(ADMIN_ID)).isZero();
	}

	@Test
	void 管理者は管理者用apiに入れ_一般は403_未ログインは401() throws Exception {
		Cookie admin = login(ADMIN_ID, adminSecret);
		Cookie general = login(GENERAL_ID, generalSecret);

		mockMvc.perform(get("/api/admin/test-only").cookie(admin)).andExpect(status().isOk());
		mockMvc.perform(get("/api/admin/test-only").cookie(general)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/test-only")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	void セッションを持ったまま再ログインするとセッションIDが変わる() throws Exception {
		Cookie first = login(ADMIN_ID, adminSecret);
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf()).cookie(first)
				.contentType("application/json").content(loginJson(ADMIN_ID, adminSecret)))
				.andExpect(status().isOk()).andReturn();
		Cookie second = result.getResponse().getCookie("SESSION");
		assertThat(second).isNotNull();
		assertThat(second.getValue()).isNotEqualTo(first.getValue());
		// 古いセッションIDでは入れない
		mockMvc.perform(get("/api/auth/me").cookie(first)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isOk());
	}

}
