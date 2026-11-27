package jp.co.apt.mony.auth;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import jp.co.apt.mony.common.code.Role;

/** app_user から本人確認用のユーザーを読み込む。 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final JdbcTemplate jdbcTemplate;

	public AppUserDetailsService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public UserDetails loadUserByUsername(String userId) throws UsernameNotFoundException {
		List<AppUser> users = jdbcTemplate.query(
				"SELECT user_id, user_name, password_hash, role FROM app_user WHERE user_id = ?",
				(rs, rowNum) -> new AppUser(rs.getString("user_id"), rs.getString("user_name"),
						rs.getString("password_hash"), Role.fromLabel(rs.getString("role"))),
				userId);
		if (users.isEmpty()) {
			throw new UsernameNotFoundException("ユーザーが見つかりません");
		}
		return users.get(0);
	}

}
