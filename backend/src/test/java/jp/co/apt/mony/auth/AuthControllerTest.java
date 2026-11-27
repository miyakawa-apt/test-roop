package jp.co.apt.mony.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

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

import jakarta.servlet.http.Cookie;
import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.config.SecurityConfig;
import jp.co.apt.mony.health.HealthController;

/** DB なしで動く WebMvcTest（ユーザーはメモリ上、JdbcTemplate はモック）。 */
@WebMvcTest({ AuthController.class, HealthController.class, TestAdminController.class })
@Import({ SecurityConfig.class, AuthControllerTest.UserConfig.class })
class AuthControllerTest {

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

	private static String loginJson(String userId, String secret) {
		return "{\"userId\":" + quote(userId) + ",\"password\":" + quote(secret) + "}";
	}

	private static String quote(String value) {
		return value == null ? "null" : "\"" + value + "\"";
	}

	private MockHttpSession login(String userId, String secret) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson(userId, secret)))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	@Test
	void ログイン成功でユーザー情報を返す() throws Exception {
		mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson(ADMIN_ID, ADMIN_SECRET)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("A001"))
				.andExpect(jsonPath("$.userName").value("管理者"))
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.roleLabel").value("管理者"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(ADMIN_SECRET))))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("$2"))));
	}

	@Test
	void パスワード不一致と存在しないIDは同じ401応答() throws Exception {
		String wrongSecret = temporaryValue();
		MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson(ADMIN_ID, wrongSecret)))
				.andExpect(status().isUnauthorized()).andReturn();
		MvcResult unknownUser = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson("Z999", wrongSecret)))
				.andExpect(status().isUnauthorized()).andReturn();

		assertThat(unknownUser.getResponse().getContentAsString())
				.isEqualTo(wrongPassword.getResponse().getContentAsString());
		assertThat(wrongPassword.getResponse().getContentAsString()).contains("AUTHENTICATION_FAILED");
	}

	@Test
	void 未入力は項目別に複数まとめて400() throws Exception {
		mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2))
				.andExpect(jsonPath("$.errors[0].field").value("userId"))
				.andExpect(jsonPath("$.errors[0].type").value("REQUIRED"))
				.andExpect(jsonPath("$.errors[1].field").value("password"))
				.andExpect(jsonPath("$.errors[1].type").value("REQUIRED"));
	}

	@Test
	void 形式と桁数の誤りも項目別にまとめて400() throws Exception {
		mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(loginJson("ab-1", "short")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2))
				.andExpect(jsonPath("$.errors[0].field").value("userId"))
				.andExpect(jsonPath("$.errors[0].type").value("INVALID_FORMAT"))
				.andExpect(jsonPath("$.errors[1].field").value("password"))
				.andExpect(jsonPath("$.errors[1].type").value("INVALID_LENGTH"));
	}

	@Test
	void 本文なしも400() throws Exception {
		mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf()).contentType("application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void 未ログインは健康確認とログイン以外の_api_を401で拒否する() throws Exception {
		mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/test-only/any-user")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/test-only")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/health")).andExpect(status().isOk());
	}

	@Test
	void ログイン後はmeでログイン者を取得できる() throws Exception {
		MockHttpSession session = login(GENERAL_ID, GENERAL_SECRET);
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("B002"))
				.andExpect(jsonPath("$.userName").value("一般太郎"))
				.andExpect(jsonPath("$.role").value("GENERAL"))
				.andExpect(jsonPath("$.roleLabel").value("一般"));
		mockMvc.perform(get("/api/test-only/any-user").session(session)).andExpect(status().isOk());
	}

	@Test
	void 一般ユーザーは管理者用_api_が403() throws Exception {
		MockHttpSession session = login(GENERAL_ID, GENERAL_SECRET);
		mockMvc.perform(get("/api/admin/test-only").session(session)).andExpect(status().isForbidden());
	}

	@Test
	void 管理者は管理者用_api_を呼べる() throws Exception {
		MockHttpSession session = login(ADMIN_ID, ADMIN_SECRET);
		mockMvc.perform(get("/api/admin/test-only").session(session)).andExpect(status().isOk());
	}

	@Test
	void ログアウト後はmeが401() throws Exception {
		MockHttpSession session = login(ADMIN_ID, ADMIN_SECRET);
		mockMvc.perform(post("/api/auth/logout").with(CsrfSupport.withCsrf()).session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void ログイン時に既存のセッションIDが変更される() throws Exception {
		MockHttpSession before = new MockHttpSession();
		String oldId = before.getId();
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf()).session(before)
				.with(request -> {
					request.setRequestedSessionId(oldId);
					return request;
				})
				.contentType("application/json").content(loginJson(ADMIN_ID, ADMIN_SECRET)))
				.andExpect(status().isOk()).andReturn();
		assertThat(result.getRequest().getSession(false).getId()).isNotEqualTo(oldId);
	}

	@Test
	void CSRFトークンなしのPOSTは403() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType("application/json")
				.content(loginJson(ADMIN_ID, ADMIN_SECRET)))
				.andExpect(status().isForbidden());
	}

	@Test
	void GETでXSRF_TOKEN_Cookieが返り_ヘッダーに載せればPOSTできる() throws Exception {
		MvcResult first = mockMvc.perform(get("/api/health")).andExpect(status().isOk()).andReturn();
		String setCookie = first.getResponse().getHeaders("Set-Cookie").stream()
				.filter(h -> h.startsWith("XSRF-TOKEN=")).findFirst().orElse(null);
		assertThat(setCookie).isNotNull();
		assertThat(setCookie).doesNotContainIgnoringCase("HttpOnly");
		String tokenValue = setCookie.substring("XSRF-TOKEN=".length(), setCookie.indexOf(';'));
		assertThat(tokenValue).isNotBlank();
		Cookie cookie = new Cookie("XSRF-TOKEN", tokenValue);

		mockMvc.perform(post("/api/auth/login").cookie(cookie).header("X-XSRF-TOKEN", tokenValue)
				.contentType("application/json").content(loginJson(ADMIN_ID, ADMIN_SECRET)))
				.andExpect(status().isOk());
	}

}
