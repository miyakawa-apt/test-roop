package jp.co.apt.mony.product;

/** 商品の応答。category は定数名、categoryLabel は表示名。 */
public record ProductResponse(String productCode, String productName, String category, String categoryLabel,
		int unitPrice, int caseQuantity) {

	static ProductResponse of(Product p) {
		return new ProductResponse(p.getProductCode(), p.getProductName(), p.getCategory().name(),
				p.getCategory().label(), p.getUnitPrice(), p.getCaseQuantity());
	}

}
