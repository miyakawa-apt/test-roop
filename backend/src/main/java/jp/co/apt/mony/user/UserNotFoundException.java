package jp.co.apt.mony.user;

/** 更新・削除の対象のユーザーがない（404。他の人に先に削除された場合など）。 */
public class UserNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public UserNotFoundException(String userId) {
		super("ユーザーが存在しません: " + userId);
	}

}
