package jp.co.apt.mony.common.validation;

/** 検証エラー種別の識別子。表示文言は後続の共通UI要件で決める。 */
public enum ErrorType {

	/** 未入力（null・空文字）。 */
	REQUIRED,
	/** 形式不正（数字以外・桁数や構成が規定外）。 */
	INVALID_FORMAT,
	/** 数値が範囲外。 */
	OUT_OF_RANGE,
	/** 文字数が範囲外。 */
	INVALID_LENGTH

}
