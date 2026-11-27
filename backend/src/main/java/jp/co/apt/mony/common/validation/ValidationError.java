package jp.co.apt.mony.common.validation;

/**
 * 検証エラー1件。
 *
 * @param field 項目名（識別子）
 * @param type エラー種別
 * @param debugMessage デバッグ用の簡易メッセージ（画面表示用ではない）
 */
public record ValidationError(String field, ErrorType type, String debugMessage) {
}
