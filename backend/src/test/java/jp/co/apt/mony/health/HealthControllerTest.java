package jp.co.apt.mony.health;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jp.co.apt.mony.config.SecurityConfig;

/** DB なしで動く WebMvcTest（JdbcTemplate はモック）。 */
@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	JdbcTemplate jdbcTemplate;

	/** SecurityConfig の認証管理が必要とするため差し替える（DB は使わない）。 */
	@MockitoBean
	UserDetailsService userDetailsService;

	@Test
	void DB接続できるとき200とUPを返す_認証なしで呼べる() throws Exception {
		when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>any())).thenReturn(1);

		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"));
	}

	@Test
	void DB接続できないとき503とDOWNを返す() throws Exception {
		when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.<Class<Integer>>any()))
				.thenThrow(new DataAccessResourceFailureException("down"));

		mockMvc.perform(get("/api/health"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.database").value("DOWN"));
	}

}
