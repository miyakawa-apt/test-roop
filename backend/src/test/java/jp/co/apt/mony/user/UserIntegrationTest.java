package jp.co.apt.mony.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import jp.co.apt.mony.auth.CsrfSupport;
import jp.co.apt.mony.common.code.Role;

/**
 * 実DB（PostgreSQL）でユーザーAPIを確かめる。環境変数 SPRING_DATASOURCE_PASSWORD があるときだけ実行する。
 * テスト用のユーザー（T001〜T00N）と関連する実績・セッションは各テストの前後で削除する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_PASSWORD", matches = ".+")
class UserIntegrationTest {

	private static final String ADMIN_ID = "T001";
	private static final String GENERAL_ID = "T002";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	UserService service;

	/**
	 * ロールバック確認用。リポジトリを包み、FAIL_AFTER に指定したメソッドを「実際に実行した直後」に例外にする。
	 */
	static final AtomicReference<String> FAIL_AFTER = new AtomicReference<>();

	@TestConfiguration
	static class FailureConfig {

		@Bean
		static BeanPostProcessor failingRepository() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (!(bean instanceof UserRepository real)) {
						return bean;
					}
					return Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
							new Class<?>[] { UserRepository.class }, (proxy, method, args) -> {
								Object result;
								try {
									result = method.invoke(real, args);
								} catch (InvocationTargetException e) {
									throw e.getCause();
								}
								if (method.getName().equals(FAIL_AFTER.get())) {
									throw new IllegalStateException("途中で失敗");
								}
								return result;
							});
				}
			};
		}

	}

	private final String adminSecret = temporaryValue();
	private final String generalSecret = temporaryValue();
	private final String newSecret = temporaryValue();
	private Cookie admin;

	private static String temporaryValue() {
		return "Tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		insertUser(ADMIN_ID, "試験管理者", adminSecret, Role.ADMIN);
		insertUser(GENERAL_ID, "試験一般", generalSecret, Role.GENERAL);
		admin = login(ADMIN_ID, adminSecret);
	}

	@AfterEach
	void cleanUp() {
		FAIL_AFTER.set(null);
		jdbc.update("DELETE FROM work_result WHERE user_id LIKE 'T00%'");
		jdbc.update("DELETE FROM spring_session WHERE principal_name LIKE 'T00%'");
		jdbc.update("DELETE FROM app_user WHERE user_id LIKE 'T00%'");
	}

	private void insertUser(String userId, String name, String rawPw, Role role) {
		jdbc.update("INSERT INTO app_user VALUES (?, ?, ?, ?)", userId, name, encoder.encode(rawPw), role.label());
	}

	private Cookie login(String userId, String secret) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"userId\":\"" + userId + "\",\"password\":\"" + secret + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return result.getResponse().getCookie("SESSION");
	}

	private static String body(String userId, String name, String rawPw, String role) {
		return "{\"userId\":\"" + userId + "\",\"userName\":\"" + name + "\",\"password\":\"" + rawPw
				+ "\",\"role\":\"" + role + "\"}";
	}

	private void register(String userId, String name, String rawPw, String role) throws Exception {
		mockMvc.perform(post("/api/admin/users").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body(userId, name, rawPw, role)))
				.andExpect(status().isCreated());
	}

	private int count(String userId) {
		return jdbc.queryForObject("SELECT count(*) FROM app_user WHERE user_id = ?", Integer.class, userId);
	}

	private String hashOf(String userId) {
		return jdbc.queryForObject("SELECT password_hash FROM app_user WHERE user_id = ?", String.class, userId);
	}

	private boolean otherAdminsExist() {
		return jdbc.queryForObject("SELECT count(*) FROM app_user WHERE role = ? AND user_id <> ?", Integer.class,
				Role.ADMIN.label(), ADMIN_ID) > 0;
	}

	@Test
	void 登録すると一覧にユーザーID順で出て_権限はDBに日本語で保存され_パスワードはハッシュで保存される() throws Exception {
		String rawPw = temporaryValue();
		register("T004", "ねじ", rawPw, "ADMIN");
		register("T003", "みかん", rawPw, "GENERAL");

		assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE user_id = 'T004'", String.class))
				.isEqualTo("管理者");
		assertThat(hashOf("T003")).isNotEqualTo(rawPw).startsWith("$2");
		assertThat(encoder.matches(rawPw, hashOf("T003"))).isTrue();

		MvcResult list = mockMvc.perform(get("/api/admin/users").cookie(admin)).andExpect(status().isOk())
				.andReturn();
		String json = list.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(json.indexOf("T003")).isGreaterThanOrEqualTo(0).isLessThan(json.indexOf("T004"));
		assertThat(json).doesNotContain("password").doesNotContain(hashOf("T003")).doesNotContain(rawPw);
		// 登録したユーザーでログインできる
		login("T003", rawPw);
	}

	@Test
	void 重複登録は409で既存の内容は変わらない() throws Exception {
		register("T003", "みかん", temporaryValue(), "GENERAL");
		String before = hashOf("T003");
		mockMvc.perform(post("/api/admin/users").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("T003", "別名", temporaryValue(), "ADMIN")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DUPLICATE"));
		assertThat(jdbc.queryForObject("SELECT user_name FROM app_user WHERE user_id = 'T003'", String.class))
				.isEqualTo("みかん");
		assertThat(hashOf("T003")).isEqualTo(before);
	}

	@Test
	void 更新できる_ユーザーIDは変わらず_パスワードなしならハッシュは変わらない() throws Exception {
		register("T003", "みかん", temporaryValue(), "GENERAL");
		String before = hashOf("T003");
		mockMvc.perform(put("/api/admin/users/T003").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("T009", "新名前", "", "ADMIN")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("T003"))
				.andExpect(jsonPath("$.roleLabel").value("管理者"));
		assertThat(count("T009")).isZero();
		assertThat(jdbc.queryForObject("SELECT user_name || role FROM app_user WHERE user_id = 'T003'",
				String.class)).isEqualTo("新名前管理者");
		assertThat(hashOf("T003")).isEqualTo(before);
	}

	@Test
	void 更新でパスワードを送ると新しいパスワードでログインできる() throws Exception {
		register("T003", "みかん", temporaryValue(), "GENERAL");
		String before = hashOf("T003");
		mockMvc.perform(put("/api/admin/users/T003").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "みかん", newSecret, "GENERAL")))
				.andExpect(status().isOk());
		assertThat(hashOf("T003")).isNotEqualTo(before).isNotEqualTo(newSecret);
		login("T003", newSecret);
	}

	@Test
	void 存在しないユーザーの更新と削除は404() throws Exception {
		mockMvc.perform(put("/api/admin/users/T009").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "名前", "", "GENERAL")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
		mockMvc.perform(delete("/api/admin/users/T009").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 削除できる() throws Exception {
		register("T003", "みかん", temporaryValue(), "GENERAL");
		mockMvc.perform(delete("/api/admin/users/T003").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNoContent());
		assertThat(count("T003")).isZero();
	}

	@Test
	void ログイン中の本人の削除は409_SELFで残る() throws Exception {
		mockMvc.perform(delete("/api/admin/users/" + ADMIN_ID).cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SELF"));
		assertThat(count(ADMIN_ID)).isEqualTo(1);
	}

	@Test
	void 最後の管理者の一般への降格は409_LAST_ADMINで変わらない() throws Exception {
		assumeTrue(!otherAdminsExist(), "他の管理者がいる環境では確認できない");
		mockMvc.perform(put("/api/admin/users/" + ADMIN_ID).cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "試験管理者", "", "GENERAL")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LAST_ADMIN"));
		assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE user_id = ?", String.class, ADMIN_ID))
				.isEqualTo("管理者");
	}

	@Test
	void 管理者が複数いれば降格できる() throws Exception {
		register("T003", "管理者2", temporaryValue(), "ADMIN");
		mockMvc.perform(put("/api/admin/users/T003").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "管理者2", "", "GENERAL")))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE user_id = 'T003'", String.class))
				.isEqualTo("一般");
	}

	@Test
	void 最後の管理者は削除できない_サービス直接() {
		assumeTrue(!otherAdminsExist(), "他の管理者がいる環境では確認できない");
		assertThatThrownBy(() -> service.delete(ADMIN_ID, "T999")).isInstanceOfSatisfying(
				UserConflictException.class,
				e -> assertThat(e.reason()).isEqualTo(UserConflictException.Reason.LAST_ADMIN));
		assertThat(count(ADMIN_ID)).isEqualTo(1);
	}

	@Test
	void 実績の履歴があるユーザーの削除は409_HAS_RESULTSでユーザーも実績も残る() throws Exception {
		register("T003", "みかん", temporaryValue(), "GENERAL");
		jdbc.update("INSERT INTO work_result (result_type, worked_at, product_code, shelf_no, quantity, "
				+ "before_quantity, after_quantity, user_id) "
				+ "VALUES ('入庫', CURRENT_TIMESTAMP, '90000001', '99-999-99', 5, 0, 5, 'T003')");
		mockMvc.perform(delete("/api/admin/users/T003").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("HAS_RESULTS"));
		assertThat(count("T003")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_result WHERE user_id = 'T003'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 入力検証エラーは400で何も登録されない() throws Exception {
		mockMvc.perform(post("/api/admin/users").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("T0", "", "short", "XXX")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(4));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE user_id LIKE 'T0%'", Integer.class))
				.isEqualTo(2); // setUp の2件だけ
	}

	@Test
	void 一般ユーザーは403_未ログインは401() throws Exception {
		Cookie general = login(GENERAL_ID, generalSecret);
		mockMvc.perform(get("/api/admin/users").cookie(general)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/users").cookie(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("T003", "みかん", temporaryValue(), "GENERAL")))
				.andExpect(status().isForbidden());
		assertThat(count("T003")).isZero();
		mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void 登録の途中で失敗するとトランザクションごとロールバックされる() {
		FAIL_AFTER.set("saveAndFlush"); // INSERT を実行した直後に失敗させる

		assertThatThrownBy(() -> service.register(
				new UserRequest("T003", "みかん", temporaryValue(), Role.GENERAL.name())))
				.isInstanceOf(IllegalStateException.class);

		assertThat(count("T003")).isZero();
	}

	@Test
	void 更新の途中で失敗するとトランザクションごとロールバックされる() {
		FAIL_AFTER.set("saveAndFlush"); // UPDATE を実行した直後に失敗させる
		String before = hashOf(GENERAL_ID);

		assertThatThrownBy(() -> service.update(GENERAL_ID,
				new UserRequest(null, "変更後", newSecret, Role.ADMIN.name())))
				.isInstanceOf(IllegalStateException.class);

		assertThat(jdbc.queryForObject("SELECT user_name || role FROM app_user WHERE user_id = ?", String.class,
				GENERAL_ID)).isEqualTo("試験一般一般");
		assertThat(hashOf(GENERAL_ID)).isEqualTo(before);
	}

	@Test
	void 削除の途中で失敗するとトランザクションごとロールバックされる() throws Exception {
		register("T003", "みかん", temporaryValue(), "GENERAL");
		FAIL_AFTER.set("flush"); // DELETE を実行した直後に失敗させる

		assertThatThrownBy(() -> service.delete("T003", ADMIN_ID)).isInstanceOf(IllegalStateException.class);

		assertThat(count("T003")).isEqualTo(1);
	}

	@Test
	void 同時に削除された後の更新と削除は404() throws Exception {
		register("T003", "みかん", temporaryValue(), "GENERAL");
		jdbc.update("DELETE FROM app_user WHERE user_id = 'T003'"); // 他の人に先に削除された
		mockMvc.perform(put("/api/admin/users/T003").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "名前", "", "GENERAL")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
		mockMvc.perform(delete("/api/admin/users/T003").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

}
