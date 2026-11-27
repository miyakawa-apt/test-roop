package jp.co.apt.mony.location;

/** 更新・削除の対象の棚がない（404。他の人に先に削除された場合など）。 */
public class LocationNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LocationNotFoundException(String shelfNo) {
		super("棚が存在しません: " + shelfNo);
	}

}
