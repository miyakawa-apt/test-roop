package jp.co.apt.mony.product;

/** 競合（409）。DUPLICATE: 商品コードが既に存在／HAS_STOCK: 在庫があるため削除できない。 */
public class ProductConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public enum Reason {
		DUPLICATE, HAS_STOCK
	}

	private final Reason reason;

	public ProductConflictException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
