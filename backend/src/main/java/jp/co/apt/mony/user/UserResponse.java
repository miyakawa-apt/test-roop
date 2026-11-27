package jp.co.apt.mony.user;

/** ユーザーの応答（パスワード・ハッシュは含めない）。role は定数名、roleLabel は表示名。 */
public record UserResponse(String userId, String userName, String role, String roleLabel) {

	static UserResponse of(UserAccount u) {
		return new UserResponse(u.getUserId(), u.getUserName(), u.getRole().name(), u.getRole().label());
	}

}
