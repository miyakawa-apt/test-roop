package jp.co.apt.mony.common.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class InputRuleTest {

	private static Optional<ErrorType> check(InputRule rule, String value) {
		return rule.check(value);
	}

	private static void ok(InputRule rule, String... values) {
		for (String v : values) {
			assertEquals(Optional.empty(), check(rule, v), rule + " / [" + v + "]");
		}
	}

	private static void ng(InputRule rule, ErrorType type, String... values) {
		for (String v : values) {
			assertEquals(Optional.of(type), check(rule, v), rule + " / [" + v + "]");
		}
	}

	@Test
	void 数量() {
		ok(InputRule.QUANTITY, "1", "99999", "00001");
		ng(InputRule.QUANTITY, ErrorType.OUT_OF_RANGE, "0", "100000", "99999999999999999999999");
		ng(InputRule.QUANTITY, ErrorType.INVALID_FORMAT, "-1", "1.5", "１２", "12 ", " 12", "1,000", "abc");
		ng(InputRule.QUANTITY, ErrorType.REQUIRED, null, "");
	}

	@Test
	void 単価() {
		ok(InputRule.UNIT_PRICE, "0", "9999999");
		ng(InputRule.UNIT_PRICE, ErrorType.OUT_OF_RANGE, "10000000");
		ng(InputRule.UNIT_PRICE, ErrorType.INVALID_FORMAT, "１００", " 100", "100 ", "-0");
		ng(InputRule.UNIT_PRICE, ErrorType.REQUIRED, null, "");
	}

	@Test
	void ケース入数() {
		ok(InputRule.CASE_QUANTITY, "1", "9999");
		ng(InputRule.CASE_QUANTITY, ErrorType.OUT_OF_RANGE, "0", "10000");
		ng(InputRule.CASE_QUANTITY, ErrorType.INVALID_FORMAT, "９", "1 ", "x");
		ng(InputRule.CASE_QUANTITY, ErrorType.REQUIRED, null, "");
	}

	@Test
	void 棚番() {
		ok(InputRule.SHELF_NO, "01-001-01", "99-999-99");
		ng(InputRule.SHELF_NO, ErrorType.INVALID_FORMAT, "1-001-01", "01-01-01", "01-001-1", "010010 1",
				"01-001-011", "01001001", "０１-００１-０１", " 01-001-01", "01-001-01 ", "01-001-01\n", "AB-001-01");
		ng(InputRule.SHELF_NO, ErrorType.REQUIRED, null, "");
	}

	@Test
	void 商品コード() {
		ok(InputRule.PRODUCT_CODE, "00000000", "12345678");
		ng(InputRule.PRODUCT_CODE, ErrorType.INVALID_FORMAT, "1234567", "123456789", "１２３４５６７８",
				" 12345678", "12345678 ", "1234567a");
		ng(InputRule.PRODUCT_CODE, ErrorType.REQUIRED, null, "");
	}

	@Test
	void 商品名() {
		ok(InputRule.PRODUCT_NAME, "a", "あ".repeat(30));
		ng(InputRule.PRODUCT_NAME, ErrorType.INVALID_LENGTH, "あ".repeat(31));
		ng(InputRule.PRODUCT_NAME, ErrorType.REQUIRED, null, "");
		// サロゲートペアは1文字として数える
		ok(InputRule.PRODUCT_NAME, "\uD842\uDFB7".repeat(30));
		ng(InputRule.PRODUCT_NAME, ErrorType.INVALID_LENGTH, "\uD842\uDFB7".repeat(31));
	}

	@Test
	void ユーザー名() {
		ok(InputRule.USER_NAME, "山", "あ".repeat(15));
		ng(InputRule.USER_NAME, ErrorType.INVALID_LENGTH, "あ".repeat(16));
		ng(InputRule.USER_NAME, ErrorType.REQUIRED, null, "");
	}

	@Test
	void ユーザーID() {
		ok(InputRule.USER_ID, "ab12", "AB12", "0000");
		ng(InputRule.USER_ID, ErrorType.INVALID_FORMAT, "abc", "abcde", "ａｂ12", "ab1 ", " ab1", "ab-1", "あいうえ");
		ng(InputRule.USER_ID, ErrorType.REQUIRED, null, "");
	}

	@Test
	void パスワード() {
		ok(InputRule.PASSWORD, "a".repeat(8), "a".repeat(14), "あ".repeat(8));
		ng(InputRule.PASSWORD, ErrorType.INVALID_LENGTH, "a".repeat(7), "a".repeat(15));
		ng(InputRule.PASSWORD, ErrorType.REQUIRED, null, "");
		// 前後空白も文字として数える（補正しない）
		ng(InputRule.PASSWORD, ErrorType.INVALID_LENGTH, " " + "a".repeat(14));
	}

	@Test
	void 備考() {
		ok(InputRule.REMARKS, null, "", "a", "あ".repeat(50));
		ng(InputRule.REMARKS, ErrorType.INVALID_LENGTH, "あ".repeat(51));
	}

	@Test
	void 理由() {
		ok(InputRule.REASON, "a", "あ".repeat(50));
		ng(InputRule.REASON, ErrorType.INVALID_LENGTH, "あ".repeat(51));
		ng(InputRule.REASON, ErrorType.REQUIRED, null, "");
	}

	@ParameterizedTest
	@NullAndEmptySource
	void 備考以外は空とnullが必須エラー(String value) {
		for (InputRule rule : InputRule.values()) {
			if (rule != InputRule.REMARKS) {
				assertEquals(Optional.of(ErrorType.REQUIRED), rule.check(value), rule.name());
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "１", "１２３４５６７８", " 1", "1 " })
	void 全角数字と空白は数字ルールで不正(String value) {
		assertEquals(Optional.of(ErrorType.INVALID_FORMAT), InputRule.QUANTITY.check(value));
	}

	@Test
	void 複数エラーをまとめて返せる() {
		ValidationResult result = new ValidationResult();
		InputRule.QUANTITY.validate(result, "0");
		InputRule.PRODUCT_CODE.validate(result, "abc");
		InputRule.PRODUCT_NAME.validate(result, "");
		InputRule.REMARKS.validate(result, "ok");
		InputRule.QUANTITY.validate(result, "outQuantity", "１");

		assertFalse(result.isValid());
		assertEquals(4, result.errors().size());
		assertEquals(java.util.List.of(ErrorType.OUT_OF_RANGE), result.typesOf("quantity"));
		assertEquals(java.util.List.of(ErrorType.INVALID_FORMAT), result.typesOf("productCode"));
		assertEquals(java.util.List.of(ErrorType.REQUIRED), result.typesOf("productName"));
		assertEquals(java.util.List.of(ErrorType.INVALID_FORMAT), result.typesOf("outQuantity"));
		assertTrue(result.errors().get(0).debugMessage().contains("quantity"));
	}

	@Test
	void 問題なければ有効() {
		assertTrue(InputRule.QUANTITY.validate("10").isValid());
	}

}
