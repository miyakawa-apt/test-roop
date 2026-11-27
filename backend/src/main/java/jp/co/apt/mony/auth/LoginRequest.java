package jp.co.apt.mony.auth;

/** ログイン要求。値は補正せずそのまま検証する。 */
public record LoginRequest(String userId, String password) {

	/** パスワードを平文で出力しない。 */
	@Override
	public String toString() {
		return "LoginRequest[userId=" + userId + "]";
	}

}
