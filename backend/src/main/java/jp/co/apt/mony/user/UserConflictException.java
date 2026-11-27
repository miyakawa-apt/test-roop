package jp.co.apt.mony.user;

/**
 * 競合（409）。DUPLICATE: ユーザーIDが既に存在／SELF: ログイン中の本人は削除できない／
 * LAST_ADMIN: 最後の管理者は削除も一般への変更もできない／HAS_RESULTS: 実績の履歴があるため削除できない。
 */
public class UserConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public enum Reason {
		DUPLICATE, SELF, LAST_ADMIN, HAS_RESULTS
	}

	private final Reason reason;

	public UserConflictException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
