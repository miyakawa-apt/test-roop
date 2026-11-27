package jp.co.apt.mony.location;

/**
 * 競合（409）。DUPLICATE: 棚番が既に存在／HAS_STOCK: 在庫があるため削除・棚番変更・禁止棚への更新ができない。
 */
public class LocationConflictException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public enum Reason {
		DUPLICATE, HAS_STOCK
	}

	private final Reason reason;

	public LocationConflictException(Reason reason) {
		super(reason.name());
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

}
