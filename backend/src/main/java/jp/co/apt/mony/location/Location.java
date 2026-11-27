package jp.co.apt.mony.location;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jp.co.apt.mony.common.code.ShelfStatus;
import jp.co.apt.mony.common.code.ShelfStatusConverter;

/**
 * ロケーション（テーブル location）。棚番は自分で決める主キーのため Persistable にして、
 * 新規かどうかを明示する（既存の棚番を save で上書きしてしまうのを防ぐ）。
 * 棚番の変更は主キーの更新になるため、リポジトリの専用クエリで行う（エンティティの値は書き換えない）。
 */
@Entity
@Table(name = "location")
public class Location implements Persistable<String> {

	@Id
	@Column(name = "shelf_no", length = 9, nullable = false, updatable = false)
	private String shelfNo;

	@Convert(converter = ShelfStatusConverter.class)
	@Column(name = "shelf_status", length = 10, nullable = false)
	private ShelfStatus shelfStatus;

	@Column(name = "remarks", length = 50)
	private String remarks;

	@Transient
	private boolean isNew = true;

	protected Location() {
	}

	public Location(String shelfNo, ShelfStatus shelfStatus, String remarks) {
		this.shelfNo = shelfNo;
		this.shelfStatus = shelfStatus;
		this.remarks = remarks;
	}

	/** 棚番以外を書き換える。 */
	public void change(ShelfStatus shelfStatus, String remarks) {
		this.shelfStatus = shelfStatus;
		this.remarks = remarks;
	}

	@Override
	public String getId() {
		return shelfNo;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		this.isNew = false;
	}

	public String getShelfNo() {
		return shelfNo;
	}

	public ShelfStatus getShelfStatus() {
		return shelfStatus;
	}

	/** 備考（未入力は null）。 */
	public String getRemarks() {
		return remarks;
	}

}
