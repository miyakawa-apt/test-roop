package jp.co.apt.mony.user;

/**
 * 登録・更新の要求。値は文字列で受け、InputRule で検証する。role は定数名（ADMIN など）。
 * 更新では userId は使わない（パスの userId が対象）。更新でパスワードが空なら変更しない。
 */
public record UserRequest(String userId, String userName, String password, String role) {

	/** パスワードをログ等に出さない。 */
	@Override
	public String toString() {
		return "UserRequest[userId=" + userId + ", userName=" + userName + ", role=" + role + "]";
	}

}
