package jp.co.apt.mony.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jp.co.apt.mony.auth.AppUser;
import jp.co.apt.mony.auth.AuthController;
import jp.co.apt.mony.auth.CsrfSupport;
import jp.co.apt.mony.common.code.Category;
import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.common.web.ValidationExceptionHandler;
import jp.co.apt.mony.config.SecurityConfig;

/** DB なしの WebMvcTest（サービスは本物、リポジトリとユーザーはモック／メモリ上）。 */
@WebMvcTest({ ProductController.class, AuthController.class })
@Import({ SecurityConfig.class, ProductService.class, ProductExceptionHandler.class,
		ValidationExceptionHandler.class, ProductControllerTest.UserConfig.class })
class ProductControllerTest {

	private static final String ADMIN_ID = "A001";
	private static final String GENERAL_ID = "B002";
	/** テストごとに生成する一時値（ソースに固定値を書かない）。 */
	private static final String ADMIN_SECRET = temporaryValue();
	private static final String GENERAL_SECRET = temporaryValue();

	private static String temporaryValue() {
		return "Tp" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	@TestConfiguration
	static class UserConfig {

		@Bean
		UserDetailsService userDetailsService(PasswordEncoder encoder) {
			Map<String, AppUser> users = Map.of(
					ADMIN_ID, new AppUser(ADMIN_ID, "管理者", encoder.encode(ADMIN_SECRET), Role.ADMIN),
					GENERAL_ID, new AppUser(GENERAL_ID, "一般太郎", encoder.encode(GENERAL_SECRET), Role.GENERAL));
			return userId -> {
				AppUser user = users.get(userId);
				if (user == null) {
					throw new UsernameNotFoundException("none");
				}
				return new AppUser(user.getUserId(), user.getUserName(), user.getPassword(), user.getRole());
			};
		}

	}

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	ProductRepository repository;

	private MockHttpSession admin;

	@BeforeEach
	void loginAdmin() throws Exception {
		admin = login(ADMIN_ID, ADMIN_SECRET);
		when(repository.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private MockHttpSession login(String userId, String secret) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"userId\":\"" + userId + "\",\"password\":\"" + secret + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static final String VALID = "{\"productCode\":\"00000001\",\"productName\":\"みかん\","
			+ "\"category\":\"FOOD\",\"unitPrice\":\"120\",\"caseQuantity\":\"24\"}";

	@Test
	void 一覧は商品コード順の配列で返る() throws Exception {
		when(repository.findAllByOrderByProductCodeAsc()).thenReturn(List.of(
				new Product("00000001", "みかん", Category.FOOD, 120, 24),
				new Product("00000002", "ねじ", Category.PARTS, 5, 100)));
		mockMvc.perform(get("/api/admin/products").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].productCode").value("00000001"))
				.andExpect(jsonPath("$[0].productName").value("みかん"))
				.andExpect(jsonPath("$[0].category").value("FOOD"))
				.andExpect(jsonPath("$[0].categoryLabel").value("食品"))
				.andExpect(jsonPath("$[0].unitPrice").value(120))
				.andExpect(jsonPath("$[0].caseQuantity").value(24))
				.andExpect(jsonPath("$[1].categoryLabel").value("部材"));
	}

	@Test
	void 商品がなければ空配列() throws Exception {
		when(repository.findAllByOrderByProductCodeAsc()).thenReturn(List.of());
		mockMvc.perform(get("/api/admin/products").session(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 登録は201で登録内容を返す() throws Exception {
		mockMvc.perform(post("/api/admin/products").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.productCode").value("00000001"))
				.andExpect(jsonPath("$.categoryLabel").value("食品"))
				.andExpect(jsonPath("$.unitPrice").value(120));
	}

	@Test
	void 数値はJSONの数値でも受け付ける() throws Exception {
		mockMvc.perform(post("/api/admin/products").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"productCode\":\"00000003\",\"productName\":\"工具\",\"category\":\"TOOL\","
						+ "\"unitPrice\":9999999,\"caseQuantity\":9999}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.unitPrice").value(9999999))
				.andExpect(jsonPath("$.caseQuantity").value(9999));
	}

	@Test
	void 商品コード重複は409_DUPLICATE() throws Exception {
		when(repository.existsById("00000001")).thenReturn(true);
		mockMvc.perform(post("/api/admin/products").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("DUPLICATE"));
	}

	@Test
	void 登録の検証エラーは複数まとめて400() throws Exception {
		mockMvc.perform(post("/api/admin/products").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"productCode\":\"1234\",\"productName\":\"\",\"category\":\"\","
						+ "\"unitPrice\":\"12a\",\"caseQuantity\":\"0\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(5))
				.andExpect(jsonPath("$.errors[0].field").value("productCode"))
				.andExpect(jsonPath("$.errors[0].type").value("INVALID_FORMAT"))
				.andExpect(jsonPath("$.errors[1].field").value("productName"))
				.andExpect(jsonPath("$.errors[1].type").value("REQUIRED"))
				.andExpect(jsonPath("$.errors[2].field").value("category"))
				.andExpect(jsonPath("$.errors[2].type").value("REQUIRED"))
				.andExpect(jsonPath("$.errors[3].field").value("unitPrice"))
				.andExpect(jsonPath("$.errors[3].type").value("INVALID_FORMAT"))
				.andExpect(jsonPath("$.errors[4].field").value("caseQuantity"))
				.andExpect(jsonPath("$.errors[4].type").value("OUT_OF_RANGE"));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 不正なカテゴリは400() throws Exception {
		mockMvc.perform(post("/api/admin/products").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content(VALID.replace("FOOD", "食品")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(1))
				.andExpect(jsonPath("$.errors[0].field").value("category"))
				.andExpect(jsonPath("$.errors[0].type").value("INVALID_FORMAT"));
	}

	@Test
	void 本文なしの登録は400() throws Exception {
		mockMvc.perform(post("/api/admin/products").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(5));
	}

	@Test
	void 更新は商品コード以外を書き換え_本文の商品コードは無視される() throws Exception {
		when(repository.findById("00000001")).thenReturn(Optional.of(
				new Product("00000001", "みかん", Category.FOOD, 120, 24)));
		mockMvc.perform(put("/api/admin/products/00000001").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"productCode\":\"99999999\",\"productName\":\"ねじ\",\"category\":\"PARTS\","
						+ "\"unitPrice\":\"5\",\"caseQuantity\":\"100\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productCode").value("00000001"))
				.andExpect(jsonPath("$.productName").value("ねじ"))
				.andExpect(jsonPath("$.categoryLabel").value("部材"));
	}

	@Test
	void 更新の対象がなければ404_NOT_FOUND() throws Exception {
		when(repository.findById("00000009")).thenReturn(Optional.empty());
		mockMvc.perform(put("/api/admin/products/00000009").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 更新の検証エラーは400() throws Exception {
		mockMvc.perform(put("/api/admin/products/00000001").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"productName\":\"" + "あ".repeat(31) + "\",\"category\":\"FOOD\","
						+ "\"unitPrice\":\"10000000\",\"caseQuantity\":\"1\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2))
				.andExpect(jsonPath("$.errors[0].field").value("productName"))
				.andExpect(jsonPath("$.errors[0].type").value("INVALID_LENGTH"))
				.andExpect(jsonPath("$.errors[1].field").value("unitPrice"))
				.andExpect(jsonPath("$.errors[1].type").value("OUT_OF_RANGE"));
	}

	@Test
	void 削除は204() throws Exception {
		Product existing = new Product("00000001", "みかん", Category.FOOD, 120, 24);
		when(repository.findById("00000001")).thenReturn(Optional.of(existing));
		mockMvc.perform(delete("/api/admin/products/00000001").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNoContent());
		verify(repository).delete(existing);
	}

	@Test
	void 在庫のある商品の削除は409_HAS_STOCK() throws Exception {
		when(repository.findById("00000001")).thenReturn(Optional.of(
				new Product("00000001", "みかん", Category.FOOD, 120, 24)));
		when(repository.countStockRows("00000001")).thenReturn(1L);
		mockMvc.perform(delete("/api/admin/products/00000001").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("HAS_STOCK"));
		verify(repository, never()).delete(any(Product.class));
	}

	@Test
	void 削除の対象がなければ404_NOT_FOUND() throws Exception {
		when(repository.findById("00000009")).thenReturn(Optional.empty());
		mockMvc.perform(delete("/api/admin/products/00000009").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 一般ユーザーは全操作が403() throws Exception {
		MockHttpSession general = login(GENERAL_ID, GENERAL_SECRET);
		mockMvc.perform(get("/api/admin/products").session(general)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/products").session(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/admin/products/00000001").session(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/products/00000001").session(general).with(CsrfSupport.withCsrf()))
				.andExpect(status().isForbidden());
		verify(repository, never()).saveAndFlush(any());
		verify(repository, never()).delete(any(Product.class));
	}

	@Test
	void 未ログインは全操作が401() throws Exception {
		mockMvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/products").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/admin/products/00000001").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/admin/products/00000001").with(CsrfSupport.withCsrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void CSRFトークンなしの変更系は403() throws Exception {
		mockMvc.perform(post("/api/admin/products").session(admin)
				.contentType("application/json").content(VALID)).andExpect(status().isForbidden());
	}

}
