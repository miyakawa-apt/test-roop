package jp.co.apt.mony.common.code;

/** 実績区分。 */
public enum ResultType {

	RECEIPT("入庫"),
	ISSUE("出庫"),
	CORRECTION("修正"),
	DELETION("削除");

	private final String label;

	ResultType(String label) {
		this.label = label;
	}

	/** 画面表示名。 */
	public String label() {
		return label;
	}

	/** 定数名（例: ADMIN）から変換する。不正値・null は IllegalArgumentException。 */
	public static ResultType fromName(String value) {
		return CodeSupport.parse(ResultType.class, value);
	}

}
