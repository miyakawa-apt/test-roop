package jp.co.apt.mony.location;

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
import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.common.code.ShelfStatus;
import jp.co.apt.mony.common.web.ValidationExceptionHandler;
import jp.co.apt.mony.config.SecurityConfig;

/** DB なしの WebMvcTest（サービスは本物、リポジトリとユーザーはモック／メモリ上）。 */
@WebMvcTest({ LocationController.class, AuthController.class })
@Import({ SecurityConfig.class, LocationService.class, LocationExceptionHandler.class,
		ValidationExceptionHandler.class, LocationControllerTest.UserConfig.class })
class LocationControllerTest {

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
	LocationRepository repository;

	private MockHttpSession admin;

	@BeforeEach
	void loginAdmin() throws Exception {
		admin = login(ADMIN_ID, ADMIN_SECRET);
		when(repository.saveAndFlush(any(Location.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	private MockHttpSession login(String userId, String secret) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login").with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"userId\":\"" + userId + "\",\"password\":\"" + secret + "\"}"))
				.andExpect(status().isOk()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private static final String VALID = "{\"shelfNo\":\"01-001-01\",\"shelfStatus\":\"EMPTY\",\"remarks\":\"入口側\"}";

	@Test
	void 一覧は棚番順の配列で返り_3状態の表示名を持つ() throws Exception {
		when(repository.findAllByOrderByShelfNoAsc()).thenReturn(List.of(
				new Location("01-001-01", ShelfStatus.EMPTY, null),
				new Location("01-001-02", ShelfStatus.STOCKED, "入口側"),
				new Location("01-001-03", ShelfStatus.PROHIBITED, "点検中")));
		mockMvc.perform(get("/api/admin/locations").session(admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].shelfNo").value("01-001-01"))
				.andExpect(jsonPath("$[0].shelfStatus").value("EMPTY"))
				.andExpect(jsonPath("$[0].shelfStatusLabel").value("空棚"))
				.andExpect(jsonPath("$[0].remarks").value(""))
				.andExpect(jsonPath("$[1].shelfStatusLabel").value("実棚"))
				.andExpect(jsonPath("$[1].remarks").value("入口側"))
				.andExpect(jsonPath("$[2].shelfStatusLabel").value("禁止棚"));
	}

	@Test
	void 棚がなければ空配列() throws Exception {
		when(repository.findAllByOrderByShelfNoAsc()).thenReturn(List.of());
		mockMvc.perform(get("/api/admin/locations").session(admin))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 登録は201で登録内容を返す() throws Exception {
		mockMvc.perform(post("/api/admin/locations").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shelfNo").value("01-001-01"))
				.andExpect(jsonPath("$.shelfStatusLabel").value("空棚"))
				.andExpect(jsonPath("$.remarks").value("入口側"));
	}

	@Test
	void 棚番重複は409_DUPLICATE() throws Exception {
		when(repository.existsById("01-001-01")).thenReturn(true);
		mockMvc.perform(post("/api/admin/locations").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("DUPLICATE"));
	}

	@Test
	void 登録の検証エラーは複数まとめて400() throws Exception {
		mockMvc.perform(post("/api/admin/locations").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"shelfNo\":\"1-001-01\",\"shelfStatus\":\"\",\"remarks\":\"" + "あ".repeat(51) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(3))
				.andExpect(jsonPath("$.errors[0].field").value("shelfNo"))
				.andExpect(jsonPath("$.errors[0].type").value("INVALID_FORMAT"))
				.andExpect(jsonPath("$.errors[1].field").value("shelfStatus"))
				.andExpect(jsonPath("$.errors[1].type").value("REQUIRED"))
				.andExpect(jsonPath("$.errors[2].field").value("remarks"))
				.andExpect(jsonPath("$.errors[2].type").value("INVALID_LENGTH"));
		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void 実棚での登録は400() throws Exception {
		mockMvc.perform(post("/api/admin/locations").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID.replace("EMPTY", "STOCKED")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(1))
				.andExpect(jsonPath("$.errors[0].field").value("shelfStatus"))
				.andExpect(jsonPath("$.errors[0].type").value("INVALID_FORMAT"));
	}

	@Test
	void 本文なしの登録は400() throws Exception {
		mockMvc.perform(post("/api/admin/locations").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void 備考と棚状況の更新は200() throws Exception {
		when(repository.findById("01-001-01")).thenReturn(Optional.of(
				new Location("01-001-01", ShelfStatus.EMPTY, null)));
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"shelfNo\":\"01-001-01\",\"shelfStatus\":\"PROHIBITED\",\"remarks\":\"点検中\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shelfNo").value("01-001-01"))
				.andExpect(jsonPath("$.shelfStatusLabel").value("禁止棚"))
				.andExpect(jsonPath("$.remarks").value("点検中"));
	}

	@Test
	void 棚番変更は新しい棚番で返る() throws Exception {
		when(repository.findById("01-001-01")).thenReturn(Optional.of(
				new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.renumber("01-001-01", "02-002-02", "空棚", "入口側")).thenReturn(1);
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"shelfNo\":\"02-002-02\",\"shelfStatus\":\"EMPTY\",\"remarks\":\"入口側\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shelfNo").value("02-002-02"));
	}

	@Test
	void 棚番変更で新棚番が既にあれば409_DUPLICATE() throws Exception {
		when(repository.findById("01-001-01")).thenReturn(Optional.of(
				new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.existsById("02-002-02")).thenReturn(true);
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID.replace("01-001-01", "02-002-02")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("DUPLICATE"));
	}

	@Test
	void 在庫のある棚の棚番変更は409_HAS_STOCK() throws Exception {
		when(repository.findById("01-001-01")).thenReturn(Optional.of(
				new Location("01-001-01", ShelfStatus.STOCKED, null)));
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID.replace("01-001-01", "02-002-02")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("HAS_STOCK"));
	}

	@Test
	void 在庫のある棚の禁止棚への更新は409_HAS_STOCK() throws Exception {
		when(repository.findById("01-001-01")).thenReturn(Optional.of(
				new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.countStockRows("01-001-01")).thenReturn(1L);
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID.replace("EMPTY", "PROHIBITED")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("HAS_STOCK"));
	}

	@Test
	void 更新の対象がなければ404_NOT_FOUND() throws Exception {
		when(repository.findById("01-001-09")).thenReturn(Optional.empty());
		mockMvc.perform(put("/api/admin/locations/01-001-09").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 更新の検証エラーは400() throws Exception {
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json")
				.content("{\"shelfNo\":\"AB-001-01\",\"shelfStatus\":\"EMPTY\",\"remarks\":\"" + "あ".repeat(51) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.length()").value(2))
				.andExpect(jsonPath("$.errors[0].field").value("shelfNo"))
				.andExpect(jsonPath("$.errors[1].field").value("remarks"));
	}

	@Test
	void 同時削除による楽観ロック例外は404_NOT_FOUND() throws Exception {
		when(repository.findById("01-001-01")).thenReturn(Optional.of(
				new Location("01-001-01", ShelfStatus.EMPTY, null)));
		when(repository.saveAndFlush(any(Location.class))).thenThrow(
				new org.springframework.orm.ObjectOptimisticLockingFailureException(Location.class, "01-001-01"));
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 削除は204() throws Exception {
		Location existing = new Location("01-001-01", ShelfStatus.PROHIBITED, null);
		when(repository.findById("01-001-01")).thenReturn(Optional.of(existing));
		mockMvc.perform(delete("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNoContent());
		verify(repository).delete(existing);
	}

	@Test
	void 在庫のある棚の削除は409_HAS_STOCK() throws Exception {
		when(repository.findById("01-001-01")).thenReturn(Optional.of(
				new Location("01-001-01", ShelfStatus.STOCKED, null)));
		mockMvc.perform(delete("/api/admin/locations/01-001-01").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error").value("HAS_STOCK"));
		verify(repository, never()).delete(any(Location.class));
	}

	@Test
	void 削除の対象がなければ404_NOT_FOUND() throws Exception {
		when(repository.findById("01-001-09")).thenReturn(Optional.empty());
		mockMvc.perform(delete("/api/admin/locations/01-001-09").session(admin).with(CsrfSupport.withCsrf()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error").value("NOT_FOUND"));
	}

	@Test
	void 一般ユーザーは全操作が403() throws Exception {
		MockHttpSession general = login(GENERAL_ID, GENERAL_SECRET);
		mockMvc.perform(get("/api/admin/locations").session(general)).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/locations").session(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/admin/locations/01-001-01").session(general).with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/locations/01-001-01").session(general).with(CsrfSupport.withCsrf()))
				.andExpect(status().isForbidden());
		verify(repository, never()).saveAndFlush(any());
		verify(repository, never()).delete(any(Location.class));
	}

	@Test
	void 未ログインは全操作が401() throws Exception {
		mockMvc.perform(get("/api/admin/locations")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/locations").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/admin/locations/01-001-01").with(CsrfSupport.withCsrf())
				.contentType("application/json").content(VALID)).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/admin/locations/01-001-01").with(CsrfSupport.withCsrf()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void CSRFトークンなしの変更系は403() throws Exception {
		mockMvc.perform(post("/api/admin/locations").session(admin)
				.contentType("application/json").content(VALID)).andExpect(status().isForbidden());
	}

}
