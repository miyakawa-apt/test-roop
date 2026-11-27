package jp.co.apt.mony.user;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jp.co.apt.mony.common.code.Role;
import jp.co.apt.mony.common.code.RoleConverter;

/**
 * ユーザー（テーブル app_user）。ユーザーIDは自分で決める主キーのため Persistable にして、
 * 新規かどうかを明示する。ログイン者を表す auth.AppUser とは別物（こちらは管理画面用の行）。
 */
@Entity
@Table(name = "app_user")
public class UserAccount implements Persistable<String> {

	@Id
	@Column(name = "user_id", length = 4, nullable = false, updatable = false)
	private String userId;

	@Column(name = "user_name", length = 15, nullable = false)
	private String userName;

	@Column(name = "password_hash", length = 255, nullable = false)
	private String passwordHash;

	@Convert(converter = RoleConverter.class)
	@Column(name = "role", length = 10, nullable = false)
	private Role role;

	@Transient
	private boolean isNew = true;

	protected UserAccount() {
	}

	public UserAccount(String userId, String userName, String passwordHash, Role role) {
		this.userId = userId;
		this.userName = userName;
		this.passwordHash = passwordHash;
		this.role = role;
	}

	/** ユーザーID・パスワード以外を書き換える（ユーザーIDは変更不可）。 */
	public void change(String userName, Role role) {
		this.userName = userName;
		this.role = role;
	}

	/** パスワードのハッシュを入れ替える。 */
	public void changePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	@Override
	public String getId() {
		return userId;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	public String getUserId() {
		return userId;
	}

	public String getUserName() {
		return userName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

}
