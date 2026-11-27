package jp.co.apt.mony.product;

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
import org.springframework.context.annotation.Bean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;
import jp.co.apt.mony.auth.CsrfSupport;
import jp.co.apt.mony.common.code.Category;
import jp.co.apt.mony.common.code.Role;

/**
 * 実DB（PostgreSQL）で商品マスタAPIを確かめる。環境変数 SPRING_DATASOURCE_PASSWORD があるときだけ実行する。
 * テスト用のユーザー（T001・T002）・商品（9000000N）・棚（99-999-99）と関連行は各テストの前後で削除する。
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_PASSWORD", matches = ".+")
class ProductIntegrationTest {

	private static final String ADMIN_ID = "T001";
	private static final String GENERAL_ID = "T002";
	private static final String SHELF = "99-999-99";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	PasswordEncoder encoder;

	@Autowired
	ProductService service;

	/**
	 * ロールバック確認用。リポジトリを包み、failAfter に指定したメソッドを「実際に実行した直後」に例外にする。
	 * （リポジトリは JDK プロキシのため Mockito の spy では実行を呼び戻せない）
	 */
	static final AtomicReference<String> FAIL_AFTER = new AtomicReference<>();

	@TestConfiguration
	static class FailureConfig {

		@Bean
		static BeanPostProcessor failingRepository() {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessAfterInitialization(Object bean, String beanName) {
					if (!(bean instanceof ProductRepository real)) {
						return bean;
					}
					return Proxy.newProxyInstance(ProductRepository.class.getClassLoader(),
							new Class<?>[] { ProductRepository.class }, (proxy, method, args) -> {
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
		jdbc.update("DELETE FROM work_result WHERE product_code LIKE '9000000%'");
		jdbc.update("DELETE FROM stock WHERE product_code LIKE '9000000%'");
		jdbc.update("DELETE FROM location WHERE shelf_no = ?", SHELF);
		jdbc.update("DELETE FROM product WHERE product_code LIKE '9000000%'");
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

	private static String body(String code, String name, String category, String price, String caseQty) {
		return "{\"productCode\":\"" + code + "\",\"productName\":\"" + name + "\",\"category\":\"" + category
				+ "\",\"unitPrice\":\"" + price + "\",\"caseQuantity\":\"" + caseQty + "\"}";
	}

	private void register(String code, String name) throws Exception {
		mockMvc.perform(post("/api/admin/products").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body(code, name, "FOOD", "120", "24")))
				.andExpect(status().isCreated());
	}

	private int count(String code) {
		return jdbc.queryForObject("SELECT count(*) FROM product WHERE product_code = ?", Integer.class, code);
	}

	private void insertStock(String code) {
		jdbc.update("INSERT INTO location (shelf_no, shelf_status) VALUES (?, '実棚')", SHELF);
		jdbc.update("INSERT INTO stock (product_code, shelf_no, quantity, received_date) VALUES (?, ?, 5, CURRENT_DATE)",
				code, SHELF);
	}

	@Test
	void 登録すると一覧に商品コード順で出て_カテゴリはDBに日本語で保存される() throws Exception {
		register("90000002", "ねじ");
		register("90000001", "みかん");

		assertThat(jdbc.queryForObject("SELECT category FROM product WHERE product_code = '90000001'", String.class))
				.isEqualTo("食品");
		MvcResult list = mockMvc.perform(get("/api/admin/products").cookie(admin)).andExpect(status().isOk())
				.andReturn();
		String json = list.getResponse().getContentAsString();
		assertThat(json.indexOf("90000001")).isGreaterThanOrEqualTo(0).isLessThan(json.indexOf("90000002"));
	}

	@Test
	void 重複登録は409で既存の内容は変わらない() throws Exception {
		register("90000001", "みかん");
		mockMvc.perform(post("/api/admin/products").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("90000001", "別名", "TOOL", "1", "1")))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DUPLICATE"));
		assertThat(jdbc.queryForObject("SELECT product_name FROM product WHERE product_code = '90000001'",
				String.class)).isEqualTo("みかん");
	}

	@Test
	void 更新できる_商品コードは変わらない() throws Exception {
		register("90000001", "みかん");
		mockMvc.perform(put("/api/admin/products/90000001").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("90000009", "ねじ", "PARTS", "5", "100")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productCode").value("90000001"))
				.andExpect(jsonPath("$.categoryLabel").value("部材"));
		assertThat(count("90000009")).isZero();
		assertThat(jdbc.queryForObject("SELECT product_name || category || unit_price || case_quantity "
				+ "FROM product WHERE product_code = '90000001'", String.class)).isEqualTo("ねじ部材5100");
	}

	@Test
	void 存在しない商品の更新と削除は404() throws Exception {
		mockMvc.perform(put("/api/admin/products/90000009").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "ねじ", "PARTS", "5", "100")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
		mockMvc.perform(delete("/api/admin/products/90000009").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 在庫のある商品の削除は409で商品も在庫も残る() throws Exception {
		register("90000001", "みかん");
		insertStock("90000001");
		mockMvc.perform(delete("/api/admin/products/90000001").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("HAS_STOCK"));
		assertThat(count("90000001")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM stock WHERE product_code = '90000001'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 在庫がなければ削除できる_実績履歴が残っていても削除でき履歴は残る() throws Exception {
		register("90000001", "みかん");
		jdbc.update("INSERT INTO work_result (result_type, worked_at, product_code, shelf_no, quantity, "
				+ "before_quantity, after_quantity, user_id) "
				+ "VALUES ('入庫', CURRENT_TIMESTAMP, '90000001', ?, 5, 0, 5, ?)", SHELF, ADMIN_ID);
		mockMvc.perform(delete("/api/admin/products/90000001").cookie(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNoContent());
		assertThat(count("90000001")).isZero();
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_result WHERE product_code = '90000001'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void 入力検証エラーは400で何も登録されない() throws Exception {
		mockMvc.perform(post("/api/admin/products").cookie(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("9000000", "", "XXX", "-1", "10000")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(5));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM product WHERE product_code LIKE '9000000%'",
				Integer.class)).isZero();
	}

	@Test
	void 一般ユーザーは403_未ログインは401() throws Exception {
		Cookie general = login(GENERAL_ID, generalSecret);
		mockMvc.perform(get("/api/admin/products").cookie(general)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/products").cookie(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("90000001", "みかん", "FOOD", "1", "1")))
				.andExpect(status().isForbidden());
		assertThat(count("90000001")).isZero();
		mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
	}

	@Test
	void 登録の途中で失敗するとトランザクションごとロールバックされる() {
		FAIL_AFTER.set("saveAndFlush"); // INSERT を実行した直後に失敗させる

		assertThatThrownBy(() -> service.register(
				new ProductRequest("90000001", "みかん", Category.FOOD.name(), "120", "24")))
				.isInstanceOf(IllegalStateException.class);

		assertThat(count("90000001")).isZero();
	}

	@Test
	void 削除の途中で失敗するとトランザクションごとロールバックされる() throws Exception {
		register("90000001", "みかん");
		FAIL_AFTER.set("flush"); // DELETE を実行した直後に失敗させる

		assertThatThrownBy(() -> service.delete("90000001")).isInstanceOf(IllegalStateException.class);

		assertThat(count("90000001")).isEqualTo(1);
	}

}
