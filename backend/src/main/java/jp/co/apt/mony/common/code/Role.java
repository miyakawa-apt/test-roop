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

	/** 表示名（DB の日本語値。例: 管理者）から変換する。不正値・null は IllegalArgumentException。 */
	public static Role fromLabel(String value) {
		if (value == null) {
			throw new IllegalArgumentException("Role に null は指定できません");
		}
		for (Role role : values()) {
			if (role.label.equals(value)) {
				return role;
			}
		}
		throw new IllegalArgumentException("Role の不正な値です: " + value);
	}

	/** Spring Security の権限名（ROLE_ADMIN / ROLE_GENERAL）。 */
	public String authority() {
		return "ROLE_" + name();
	}

}
