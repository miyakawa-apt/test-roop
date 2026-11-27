package jp.co.apt.mony.auth;

/**
 * ログイン者の情報。
 *
 * @param userId ユーザーID
 * @param userName 氏名
 * @param role 権限の定数名（ADMIN / GENERAL）
 * @param roleLabel 権限の表示名（管理者 / 一般）
 */
public record CurrentUserResponse(String userId, String userName, String role, String roleLabel) {

	static CurrentUserResponse of(AppUser user) {
		return new CurrentUserResponse(user.getUserId(), user.getUserName(), user.getRole().name(),
				user.getRole().label());
	}

}
