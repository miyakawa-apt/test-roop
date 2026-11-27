package jp.co.apt.mony.location;

/** ロケーションの応答。shelfStatus は定数名、shelfStatusLabel は表示名。備考が未入力なら空文字。 */
public record LocationResponse(String shelfNo, String shelfStatus, String shelfStatusLabel, String remarks) {

	static LocationResponse of(Location l) {
		return new LocationResponse(l.getShelfNo(), l.getShelfStatus().name(), l.getShelfStatus().label(),
				l.getRemarks() == null ? "" : l.getRemarks());
	}

}
