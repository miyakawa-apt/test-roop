package jp.co.apt.mony.common.validation;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 入力ルール（DT-01）。値は補正せず（trim・全角→半角変換なし）そのまま検証する。
 * 文字数は文字（コードポイント）数で数える。
 */
public enum InputRule {

	/** 数量（入庫・出庫・修正後）: 数字のみ 1〜99,999。 */
	QUANTITY("quantity", Kind.NUMBER, 1, 99_999),
	/** 単価: 数字のみ 0〜9,999,999。 */
	UNIT_PRICE("unitPrice", Kind.NUMBER, 0, 9_999_999),
	/** ケース入数: 数字のみ 1〜9,999。 */
	CASE_QUANTITY("caseQuantity", Kind.NUMBER, 1, 9_999),
	/** 棚番: NN-NNN-NN（数字のみ）。 */
	SHELF_NO("shelfNo", Kind.PATTERN, Pattern.compile("[0-9]{2}-[0-9]{3}-[0-9]{2}")),
	/** 商品コード: 数字のみ8桁。 */
	PRODUCT_CODE("productCode", Kind.PATTERN, Pattern.compile("[0-9]{8}")),
	/** 商品名: 1〜30文字。 */
	PRODUCT_NAME("productName", Kind.TEXT, 1, 30),
	/** ユーザー名: 1〜15文字。 */
	USER_NAME("userName", Kind.TEXT, 1, 15),
	/** ユーザーID: 半角英数4文字。 */
	USER_ID("userId", Kind.PATTERN, Pattern.compile("[A-Za-z0-9]{4}")),
	/** パスワード: 8〜14文字。 */
	PASSWORD("password", Kind.TEXT, 8, 14),
	/** 備考: 0〜50文字（空・null可）。 */
	REMARKS("remarks", Kind.TEXT, 0, 50),
	/** 理由: 1〜50文字。 */
	REASON("reason", Kind.TEXT, 1, 50);

	private enum Kind {
		NUMBER, TEXT, PATTERN
	}

	private static final Pattern DIGITS = Pattern.compile("[0-9]+");

	private final String fieldName;
	private final Kind kind;
	private final long min;
	private final long max;
	private final Pattern pattern;

	InputRule(String fieldName, Kind kind, long min, long max) {
		this.fieldName = fieldName;
		this.kind = kind;
		this.min = min;
		this.max = max;
		this.pattern = null;
	}

	InputRule(String fieldName, Kind kind, Pattern pattern) {
		this.fieldName = fieldName;
		this.kind = kind;
		this.min = 0;
		this.max = 0;
		this.pattern = pattern;
	}

	/** 既定の項目名。 */
	public String fieldName() {
		return fieldName;
	}

	/** 値を検証し、エラーがあれば種別を返す（なければ empty）。 */
	public Optional<ErrorType> check(String value) {
		boolean empty = value == null || value.isEmpty();
		if (empty && !(kind == Kind.TEXT && min == 0)) {
			return Optional.of(ErrorType.REQUIRED);
		}
		return switch (kind) {
			case NUMBER -> checkNumber(value);
			case TEXT -> checkText(value);
			case PATTERN -> pattern.matcher(value).matches() ? Optional.empty()
					: Optional.of(ErrorType.INVALID_FORMAT);
		};
	}

	private Optional<ErrorType> checkNumber(String value) {
		if (!DIGITS.matcher(value).matches()) {
			return Optional.of(ErrorType.INVALID_FORMAT);
		}
		// 先頭の 0 を除いた桁数が 18 を超えるものは long に収まらないため範囲外
		String significant = value.replaceFirst("^0+(?=.)", "");
		if (significant.length() > 18) {
			return Optional.of(ErrorType.OUT_OF_RANGE);
		}
		long number = Long.parseLong(significant);
		return number < min || number > max ? Optional.of(ErrorType.OUT_OF_RANGE) : Optional.empty();
	}

	private Optional<ErrorType> checkText(String value) {
		int length = value == null ? 0 : value.codePointCount(0, value.length());
		return length < min || length > max ? Optional.of(ErrorType.INVALID_LENGTH) : Optional.empty();
	}

	/** 検証して、エラーがあれば既定の項目名で result に追加する。 */
	public ValidationResult validate(ValidationResult result, String value) {
		return validate(result, fieldName, value);
	}

	/** 検証して、エラーがあれば指定の項目名で result に追加する。 */
	public ValidationResult validate(ValidationResult result, String field, String value) {
		check(value).ifPresent(type -> result.add(new ValidationError(field, type,
				field + " の検証エラー: " + type + " (" + name() + ")")));
		return result;
	}

	/** 単独の値を検証して新しい結果を返す。 */
	public ValidationResult validate(String value) {
		return validate(new ValidationResult(), value);
	}

}
