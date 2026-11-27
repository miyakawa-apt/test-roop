package jp.co.apt.mony.common.code;

/** 棚状況。 */
public enum ShelfStatus {

	EMPTY("空棚"),
	STOCKED("実棚"),
	PROHIBITED("禁止棚");

	private final String label;

	ShelfStatus(String label) {
		this.label = label;
	}

	/** 画面表示名。 */
	public String label() {
		return label;
	}

	/** 定数名（例: ADMIN）から変換する。不正値・null は IllegalArgumentException。 */
	public static ShelfStatus fromName(String value) {
		return CodeSupport.parse(ShelfStatus.class, value);
	}

}
