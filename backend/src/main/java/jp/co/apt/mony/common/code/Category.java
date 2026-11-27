package jp.co.apt.mony.common.code;

/** カテゴリ。 */
public enum Category {

	FOOD("食品"),
	DAILY_GOODS("日用品"),
	TOOL("工具"),
	PARTS("部材"),
	OTHER("その他");

	private final String label;

	Category(String label) {
		this.label = label;
	}

	/** 画面表示名。 */
	public String label() {
		return label;
	}

	/** 定数名（例: ADMIN）から変換する。不正値・null は IllegalArgumentException。 */
	public static Category fromName(String value) {
		return CodeSupport.parse(Category.class, value);
	}

}
