package jp.co.apt.mony.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
 * 実DB（PostgreSQL）でロケーションAPIを確かめる。環境変数 SPRING_DATASOURCE_PASSWORD があるときだけ実行する。
 * テスト用のユーザー（T001・T002）・商品（90000501）・棚（98-xxx-xx）と関連行は各テストの前後で削除する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_PASSWORD", matches = ".+")
class LocationIntegrationTest {

	private static final String ADMIN_ID = "T001";
	private static final String GENERAL_ID = "T002";
	private static final String PRODUCT = "90000501";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	LocationService service;

	/** ロールバック確認用。リポジトリを包み、指定したメソッドを「実際に実行した直後」に例外にする。 */
	static final AtomicReference<String> FAIL_AFTER = new AtomicReference<>();

	@TestConfiguration
	static class FailureConfig {

		@Bean
		static BeanPostProcessor failingRepository() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (!(bean instanceof LocationRepository real)) {
						return bean;
					}
					return Proxy.newProxyInstance(LocationRepository.class.getClassLoader(),
							new Class<?>[] { LocationRepository.class }, (proxy, method, args) -> {
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
	private Cookie admin;

	private static String temporaryValue() {
		return "Tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		jdbc.update("INSERT INTO app_user VALUES (?, ?, ?, ?)", ADMIN_ID, "試験管理者", encoder.encode(adminSecret),
				Role.ADMIN.label());
		jdbc.update("INSERT INTO app_user VALUES (?, ?, ?, ?)", GENERAL_ID, "試験一般", encoder.encode(generalSecret),
				Role.GENERAL.label());
		admin = login(ADMIN_ID, adminSecret);
	}

	@AfterEach
	void cleanUp() {
		FAIL_AFTER.set(null);
		jdbc.update("DELETE FROM stock WHERE shelf_no LIKE '98-%'");
		jdbc.update("DELETE FROM location WHERE shelf_no LIKE '98-%'");
		jdbc.update("DELETE FROM product WHERE product_code = ?", PRODUCT);
		jdbc.update("DELETE FROM spring_session WHERE principal_name IN (?, ?)", ADMIN_ID, GENERAL_ID);
		jdbc.update("DELETE FROM app_user WHERE user_id IN (?, ?)", ADMIN_ID, GENERAL_ID);
	}

	private Cookie login(String userId, String secret) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"userId\":\"" + userId + "\",\"password\":\"" + secret + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return result.getResponse().getCookie("SESSION");
	}

	private static String body(String shelfNo, String status, String remarks) {
		return "{\"shelfNo\":\"" + shelfNo + "\",\"shelfStatus\":\"" + status + "\",\"remarks\":\"" + remarks + "\"}";
	}

	private void register(String shelfNo, String status, String remarks) throws Exception {
		mockMvc.perform(post("/api/admin/locations").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body(shelfNo, status, remarks)))
				.andExpect(status().isCreated());
	}

	private int count(String shelfNo) {
		return jdbc.queryForObject("SELECT count(*) FROM location WHERE shelf_no = ?", Integer.class, shelfNo);
	}

	/** 棚に在庫を入れ、棚状況を実棚にする（入出庫の機能は別イシューのため、ここでは直接入れる）。 */
	private void insertStock(String shelfNo) {
		jdbc.update("INSERT INTO product VALUES (?, 'みかん', '食品', 120, 24) ON CONFLICT DO NOTHING", PRODUCT);
		jdbc.update("INSERT INTO stock (product_code, shelf_no, quantity, received_date) VALUES (?, ?, 5, CURRENT_DATE)",
				PRODUCT, shelfNo);
		jdbc.update("UPDATE location SET shelf_status = '実棚' WHERE shelf_no = ?", shelfNo);
	}

	@Test
	void 登録すると一覧に棚番順で出て_棚状況はDBに日本語で保存される() throws Exception {
		register("98-001-02", "PROHIBITED", "点検中");
		register("98-001-01", "EMPTY", "");

		assertThat(jdbc.queryForObject("SELECT shelf_status FROM location WHERE shelf_no = '98-001-02'", String.class))
				.isEqualTo("禁止棚");
		assertThat(jdbc.queryForObject("SELECT remarks FROM location WHERE shelf_no = '98-001-01'", String.class))
				.isNull();
		MvcResult list = mockMvc.perform(get("/api/admin/locations").cookie(admin)).andExpect(status().isOk())
				.andReturn();
		String json = list.getResponse().getContentAsString();
		assertThat(json.indexOf("98-001-01")).isGreaterThanOrEqualTo(0).isLessThan(json.indexOf("98-001-02"));
	}

	@Test
	void 重複登録は409で既存の内容は変わらない() throws Exception {
		register("98-001-01", "EMPTY", "元");
		mockMvc.perform(post("/api/admin/locations").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-001-01", "PROHIBITED", "別")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DUPLICATE"));
		assertThat(jdbc.queryForObject("SELECT remarks FROM location WHERE shelf_no = '98-001-01'", String.class))
				.isEqualTo("元");
	}

	@Test
	void 備考と棚状況を更新できる() throws Exception {
		register("98-001-01", "EMPTY", "");
		mockMvc.perform(put("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-001-01", "PROHIBITED", "点検中")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shelfStatusLabel").value("禁止棚"));
		assertThat(jdbc.queryForObject("SELECT shelf_status || remarks FROM location WHERE shelf_no = '98-001-01'",
				String.class)).isEqualTo("禁止棚点検中");
	}

	@Test
	void 在庫がなければ棚番を変更できる() throws Exception {
		register("98-001-01", "EMPTY", "元");
		mockMvc.perform(put("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-002-02", "PROHIBITED", "移設")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shelfNo").value("98-002-02"));
		assertThat(count("98-001-01")).isZero();
		assertThat(jdbc.queryForObject("SELECT shelf_status || remarks FROM location WHERE shelf_no = '98-002-02'",
				String.class)).isEqualTo("禁止棚移設");
	}

	@Test
	void 棚番変更で新棚番が既にあれば409で両方の棚は変わらない() throws Exception {
		register("98-001-01", "EMPTY", "A");
		register("98-002-02", "EMPTY", "B");
		mockMvc.perform(put("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-002-02", "EMPTY", "A2")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DUPLICATE"));
		assertThat(jdbc.queryForObject("SELECT remarks FROM location WHERE shelf_no = '98-001-01'", String.class))
				.isEqualTo("A");
		assertThat(jdbc.queryForObject("SELECT remarks FROM location WHERE shelf_no = '98-002-02'", String.class))
				.isEqualTo("B");
	}

	@Test
	void 在庫のある棚は棚番を変更できず409_HAS_STOCK() throws Exception {
		register("98-001-01", "EMPTY", "");
		insertStock("98-001-01");
		mockMvc.perform(put("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-002-02", "STOCKED", "")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("HAS_STOCK"));
		assertThat(count("98-001-01")).isEqualTo(1);
		assertThat(count("98-002-02")).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM stock WHERE shelf_no = '98-001-01'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 在庫のある棚は禁止棚へ更新できず409_備考だけなら更新できる() throws Exception {
		register("98-001-01", "EMPTY", "");
		insertStock("98-001-01");
		mockMvc.perform(put("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-001-01", "PROHIBITED", "")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("HAS_STOCK"));
		assertThat(jdbc.queryForObject("SELECT shelf_status FROM location WHERE shelf_no = '98-001-01'", String.class))
				.isEqualTo("実棚");

		mockMvc.perform(put("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-001-01", "STOCKED", "上段")))
				.andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT shelf_status || remarks FROM location WHERE shelf_no = '98-001-01'",
				String.class)).isEqualTo("実棚上段");
	}

	@Test
	void 存在しない棚の更新と削除は404() throws Exception {
		mockMvc.perform(put("/api/admin/locations/98-009-09").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-009-09", "EMPTY", "")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
		mockMvc.perform(delete("/api/admin/locations/98-009-09").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 在庫のある棚の削除は409で棚も在庫も残る() throws Exception {
		register("98-001-01", "EMPTY", "");
		insertStock("98-001-01");
		mockMvc.perform(delete("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("HAS_STOCK"));
		assertThat(count("98-001-01")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM stock WHERE shelf_no = '98-001-01'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 在庫がなければ空棚も禁止棚も削除できる() throws Exception {
		register("98-001-01", "EMPTY", "");
		register("98-001-02", "PROHIBITED", "");
		mockMvc.perform(delete("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/api/admin/locations/98-001-02").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNoContent());
		assertThat(count("98-001-01")).isZero();
		assertThat(count("98-001-02")).isZero();
	}

	@Test
	void 確認の後に他の人に削除された棚の更新と削除は404() throws Exception {
		register("98-001-01", "EMPTY", "");
		jdbc.update("DELETE FROM location WHERE shelf_no = '98-001-01'");
		mockMvc.perform(put("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-001-01", "PROHIBITED", "")))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/admin/locations/98-001-01").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	void 入力検証エラーは400で何も登録されない() throws Exception {
		mockMvc.perform(post("/api/admin/locations").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-001-1", "", "あ".repeat(51))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(3));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM location WHERE shelf_no LIKE '98-%'", Integer.class))
				.isZero();
	}

	@Test
	void 一般ユーザーは403_未ログインは401() throws Exception {
		Cookie general = login(GENERAL_ID, generalSecret);
		mockMvc.perform(get("/api/admin/locations").cookie(general)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/locations").cookie(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("98-001-01", "EMPTY", "")))
				.andExpect(status().isForbidden());
		assertThat(count("98-001-01")).isZero();
		mockMvc.perform(get("/api/admin/locations")).andExpect(status().isUnauthorized());
	}

	@Test
	void 登録の途中で失敗するとトランザクションごとロールバックされる() {
		FAIL_AFTER.set("saveAndFlush"); // INSERT を実行した直後に失敗させる

		assertThatThrownBy(() -> service.register(new LocationRequest("98-001-01", "EMPTY", "")))
				.isInstanceOf(IllegalStateException.class);

		assertThat(count("98-001-01")).isZero();
	}

	@Test
	void 棚番変更の途中で失敗するとトランザクションごとロールバックされる() throws Exception {
		register("98-001-01", "EMPTY", "元");
		FAIL_AFTER.set("renumber"); // UPDATE を実行した直後に失敗させる

		assertThatThrownBy(() -> service.update("98-001-01", new LocationRequest("98-002-02", "PROHIBITED", "新")))
				.isInstanceOf(IllegalStateException.class);

		assertThat(count("98-001-01")).isEqualTo(1);
		assertThat(count("98-002-02")).isZero();
		assertThat(jdbc.queryForObject("SELECT shelf_status || remarks FROM location WHERE shelf_no = '98-001-01'",
				String.class)).isEqualTo("空棚元");
	}

	@Test
	void 削除の途中で失敗するとトランザクションごとロールバックされる() throws Exception {
		register("98-001-01", "EMPTY", "");
		FAIL_AFTER.set("flush"); // DELETE を実行した直後に失敗させる

		assertThatThrownBy(() -> service.delete("98-001-01")).isInstanceOf(IllegalStateException.class);

		assertThat(count("98-001-01")).isEqualTo(1);
	}

}
