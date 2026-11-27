package jp.co.apt.mony.product;

/** 更新・削除の対象の商品がない（404。他の人に先に削除された場合など）。 */
public class ProductNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ProductNotFoundException(String code) {
		super("商品が存在しません: " + code);
	}

}
