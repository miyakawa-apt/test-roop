package jp.co.apt.mony.common.code;

/** DB に日本語の表示名（label）で保存される区分値 enum の共通インターフェース。 */
public interface Labeled {

	/** 画面表示名（DB に保存される日本語値）。 */
	String label();

}
