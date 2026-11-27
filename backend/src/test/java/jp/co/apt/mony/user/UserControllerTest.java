package jp.co.apt.mony.user;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jp.co.apt.mony.auth.AppUser;
import jp.co.apt.mony.auth.AuthController;
import jp.co.apt.mony.auth.CsrfSupport;
import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.common.web.ValidationExceptionHandler;
import jp.co.apt.mony.config.SecurityConfig;

/** DB なしの WebMvcTest（サービスは本物、リポジトリとログインユーザーはモック／メモリ上）。 */
@WebMvcTest({ UserController.class, AuthController.class })
@Import({ SecurityConfig.class, UserService.class, UserExceptionHandler.class,
		ValidationExceptionHandler.class, UserControllerTest.UserConfig.class })
class UserControllerTest {

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

	@Autowired
	PasswordEncoder encoder;

	@MockitoBean
	JdbcTemplate jdbcTemplate;

	@MockitoBean
	UserRepository repository;

	private MockHttpSession admin;

	@BeforeEach
	void loginAdmin() throws Exception {
		admin = login(ADMIN_ID, ADMIN_SECRET);
		when(repository.saveAndFlush(any(UserAccount.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private MockHttpSession login(String userId, String secret) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"userId\":\"" + userId + "\",\"password\":\"" + secret + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private UserAccount account(String userId, String name, Role role) {
		return new UserAccount(userId, name, encoder.encode(temporaryValue()), role);
	}

	private static String body(String userId, String name, String rawPw, String role) {
		return "{\"userId\":\"" + userId + "\",\"userName\":\"" + name + "\",\"password\":\"" + rawPw
				+ "\",\"role\":\"" + role + "\"}";
	}

	private final String validSecret = temporaryValue();

	@Test
	void 一覧はユーザーID順の配列で返り_パスワードもハッシュも含まない() throws Exception {
		UserAccount a = account("A001", "管理者", Role.ADMIN);
		when(repository.findAllByOrderByUserIdAsc()).thenReturn(List.of(a, account("B002", "一般太郎", Role.GENERAL)));
		MvcResult result = mockMvc.perform(get("/api/admin/users").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].userId").value("A001"))
				.andExpect(jsonPath("$[0].userName").value("管理者"))
				.andExpect(jsonPath("$[0].role").value("ADMIN"))
				.andExpect(jsonPath("$[0].roleLabel").value("管理者"))
				.andExpect(jsonPath("$[1].role").value("GENERAL"))
				.andExpect(jsonPath("$[1].roleLabel").value("一般"))
				.andExpect(jsonPath("$[0].password").doesNotExist())
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist())
				.andReturn();
		assertThat(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
				.doesNotContain(a.getPasswordHash()).doesNotContain("$2a$");
	}

	@Test
	void ユーザーがいなければ空配列() throws Exception {
		when(repository.findAllByOrderByUserIdAsc()).thenReturn(List.of());
		mockMvc.perform(get("/api/admin/users").session(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 登録は201で登録内容を返し_応答にパスワードは含まず_保存はハッシュ() throws Exception {
		mockMvc.perform(post("/api/admin/users").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("U001", "山田太郎", validSecret, "GENERAL")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").value("U001"))
				.andExpect(jsonPath("$.userName").value("山田太郎"))
				.andExpect(jsonPath("$.role").value("GENERAL"))
				.andExpect(jsonPath("$.roleLabel").value("一般"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
		ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
		verify(repository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getPasswordHash()).isNotEqualTo(validSecret);
		assertThat(encoder.matches(validSecret, saved.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void ユーザーID重複は409_DUPLICATE() throws Exception {
		when(repository.existsById("U001")).thenReturn(true);
		mockMvc.perform(post("/api/admin/users").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("U001", "山田太郎", validSecret, "GENERAL")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("DUPLICATE"));
	}

	@Test
	void 登録の検証エラーは複数まとめて400() throws Exception {
		mockMvc.perform(post("/api/admin/users").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("ab", "", "short", "")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(4))
				.andExpect(jsonPath("$.errors[0].field").value("userId"))
				.andExpect(jsonPath("$.errors[0].type").value("INVALID_FORMAT"))
				.andExpect(jsonPath("$.errors[1].field").value("userName"))
				.andExpect(jsonPath("$.errors[1].type").value("REQUIRED"))
				.andExpect(jsonPath("$.errors[2].field").value("password"))
				.andExpect(jsonPath("$.errors[2].type").value("INVALID_LENGTH"))
				.andExpect(jsonPath("$.errors[3].field").value("role"))
				.andExpect(jsonPath("$.errors[3].type").value("REQUIRED"));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 本文なしの登録は400() throws Exception {
		mockMvc.perform(post("/api/admin/users").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(4));
	}

	@Test
	void 更新はユーザーID以外を書き換え_本文のユーザーIDは無視される() throws Exception {
		UserAccount u = account("U001", "元の名前", Role.GENERAL);
		String before = u.getPasswordHash();
		when(repository.findById("U001")).thenReturn(Optional.of(u));
		mockMvc.perform(put("/api/admin/users/U001").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content("{\"userId\":\"ZZZZ\",\"userName\":\"新名前\",\"role\":\"ADMIN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value("U001"))
				.andExpect(jsonPath("$.userName").value("新名前"))
				.andExpect(jsonPath("$.roleLabel").value("管理者"));
		assertThat(u.getPasswordHash()).isEqualTo(before); // パスワードなし→変更しない
	}

	@Test
	void 更新でパスワードを送るとハッシュが変わる() throws Exception {
		UserAccount u = account("U001", "元の名前", Role.GENERAL);
		String before = u.getPasswordHash();
		when(repository.findById("U001")).thenReturn(Optional.of(u));
		mockMvc.perform(put("/api/admin/users/U001").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "元の名前", validSecret, "GENERAL")))
				.andExpect(status().isOk());
		assertThat(u.getPasswordHash()).isNotEqualTo(before);
		assertThat(encoder.matches(validSecret, u.getPasswordHash())).isTrue();
	}

	@Test
	void 更新の対象がなければ404_NOT_FOUND() throws Exception {
		when(repository.findById("U009")).thenReturn(Optional.empty());
		mockMvc.perform(put("/api/admin/users/U009").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "名前", "", "GENERAL")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 更新の検証エラーは400() throws Exception {
		mockMvc.perform(put("/api/admin/users/U001").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "あ".repeat(16), "short", "NOPE")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(3))
				.andExpect(jsonPath("$.errors[0].field").value("userName"))
				.andExpect(jsonPath("$.errors[1].field").value("password"))
				.andExpect(jsonPath("$.errors[2].field").value("role"));
	}

	@Test
	void 最後の管理者の降格は409_LAST_ADMIN() throws Exception {
		UserAccount a = account("A001", "管理者", Role.ADMIN);
		when(repository.findById("A001")).thenReturn(Optional.of(a));
		when(repository.lockAllByRole(Role.ADMIN)).thenReturn(List.of(a));
		mockMvc.perform(put("/api/admin/users/A001").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "管理者", "", "GENERAL")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("LAST_ADMIN"));
	}

	@Test
	void 更新中に同時削除されたら404() throws Exception {
		when(repository.findById("U001")).thenReturn(Optional.of(account("U001", "名前", Role.GENERAL)));
		when(repository.saveAndFlush(any(UserAccount.class)))
				.thenThrow(new ObjectOptimisticLockingFailureException(UserAccount.class, "U001"));
		mockMvc.perform(put("/api/admin/users/U001").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(body("", "名前", "", "GENERAL")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 削除は204() throws Exception {
		UserAccount u = account("U001", "名前", Role.GENERAL);
		when(repository.findById("U001")).thenReturn(Optional.of(u));
		mockMvc.perform(delete("/api/admin/users/U001").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNoContent());
		verify(repository).delete(u);
	}

	@Test
	void ログイン中の本人の削除は409_SELF() throws Exception {
		when(repository.findById("A001")).thenReturn(Optional.of(account("A001", "管理者", Role.ADMIN)));
		mockMvc.perform(delete("/api/admin/users/A001").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("SELF"));
		verify(repository, never()).delete(any(UserAccount.class));
	}

	@Test
	void 最後の管理者の削除は409_LAST_ADMIN() throws Exception {
		UserAccount other = account("A002", "管理者2", Role.ADMIN);
		when(repository.findById("A002")).thenReturn(Optional.of(other));
		when(repository.lockAllByRole(Role.ADMIN)).thenReturn(List.of(other));
		mockMvc.perform(delete("/api/admin/users/A002").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("LAST_ADMIN"));
		verify(repository, never()).delete(any(UserAccount.class));
	}

	@Test
	void 実績のあるユーザーの削除は409_HAS_RESULTS() throws Exception {
		when(repository.findById("U001")).thenReturn(Optional.of(account("U001", "名前", Role.GENERAL)));
		when(repository.countResultRows("U001")).thenReturn(1L);
		mockMvc.perform(delete("/api/admin/users/U001").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("HAS_RESULTS"));
		verify(repository, never()).delete(any(UserAccount.class));
	}

	@Test
	void 削除の対象がなければ404_NOT_FOUND() throws Exception {
		when(repository.findById("U009")).thenReturn(Optional.empty());
		mockMvc.perform(delete("/api/admin/users/U009").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 一般ユーザーは全操作が403() throws Exception {
		MockHttpSession general = login(GENERAL_ID, GENERAL_SECRET);
		String json = body("U001", "山田太郎", validSecret, "GENERAL");
		mockMvc.perform(get("/api/admin/users").session(general)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/users").session(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(json)).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/admin/users/U001").session(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(json)).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/users/U001").session(general).with(CsrfSupport.withCsrf()))
				.andExpect(status().isForbidden());
		verify(repository, never()).saveAndFlush(any());
		verify(repository, never()).delete(any(UserAccount.class));
	}

	@Test
	void 未ログインは全操作が401() throws Exception {
		String json = body("U001", "山田太郎", validSecret, "GENERAL");
		mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/users").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(json)).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/admin/users/U001").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(json)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/admin/users/U001").with(CsrfSupport.withCsrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void CSRFトークンなしの変更系は403() throws Exception {
		mockMvc.perform(post("/api/admin/users").session(admin)
				.contentType("application/json").content(body("U001", "山田太郎", validSecret, "GENERAL")))
				.andExpect(status().isForbidden());
	}

}
