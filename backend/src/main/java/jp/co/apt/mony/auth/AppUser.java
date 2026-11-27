package jp.co.apt.mony.auth;

import java.io.Serial;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import jp.co.apt.mony.common.code.Role;

/** ログイン者。ユーザーID・氏名・権限を持つ（セッションに保存されるため Serializable）。 */
public class AppUser extends User {

	@Serial
	private static final long serialVersionUID = 1L;

	private final String userName;
	private final Role role;

	public AppUser(String userId, String userName, String passwordHash, Role role) {
		super(userId, passwordHash, List.of(new SimpleGrantedAuthority(role.authority())));
		this.userName = userName;
		this.role = role;
	}

	public String getUserId() {
		return getUsername();
	}

	public String getUserName() {
		return userName;
	}

	public Role getRole() {
		return role;
	}

}
