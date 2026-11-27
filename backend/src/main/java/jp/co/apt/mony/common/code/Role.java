package jp.co.apt.mony.common.code;

/** 権限。 */
public enum Role {

	ADMIN("管理者"),
	GENERAL("一般");

	private final String label;

	Role(String label) {
		this.label = label;
	}

	/** 画面表示名。 */
	public String label() {
		return label;
	}

	/** 定数名（例: ADMIN）から変換する。不正値・null は IllegalArgumentException。 */
	public static Role fromName(String value) {
		return CodeSupport.parse(Role.class, value);
	}

}
