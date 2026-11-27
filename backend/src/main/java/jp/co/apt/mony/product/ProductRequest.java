package jp.co.apt.mony.product;

/**
 * 登録・更新の要求。値は文字列で受け、InputRule で検証する（数値は JSON の数値でも文字列でも可）。
 * category は定数名（FOOD など）。更新では productCode は使わない（パスの商品コードが対象）。
 */
public record ProductRequest(String productCode, String productName, String category, String unitPrice,
		String caseQuantity) {
}
