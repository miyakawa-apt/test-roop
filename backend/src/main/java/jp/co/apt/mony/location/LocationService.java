package jp.co.apt.mony.location;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.OptimisticLockException;
import jp.co.apt.mony.common.code.ShelfStatus;
import jp.co.apt.mony.common.validation.ErrorType;
import jp.co.apt.mony.common.validation.InputRule;
import jp.co.apt.mony.common.validation.ValidationError;
import jp.co.apt.mony.common.validation.ValidationFailedException;
import jp.co.apt.mony.common.validation.ValidationResult;

/** ロケーションの業務処理（FR-08）。登録・更新・削除はそれぞれ1トランザクション。 */
@Service
public class LocationService {

	private static final LocationRequest EMPTY = new LocationRequest(null, null, null);
	/** 在庫が棚を参照する外部キー名（schema.sql）。 */
	private static final String STOCK_FK = "fk_stock_location";

	private final LocationRepository repository;

	public LocationService(LocationRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<Location> list() {
		return repository.findAllByOrderByShelfNoAsc();
	}

	@Transactional
	public Location register(LocationRequest request) {
		LocationRequest r = request == null ? EMPTY : request;
		ValidationResult validation = new ValidationResult();
		InputRule.SHELF_NO.validate(validation, r.shelfNo());
		ShelfStatus status = validateStatus(validation, r.shelfStatus());
		InputRule.REMARKS.validate(validation, r.remarks());
		// 実棚は在庫が入ったときにだけ変わる（登録では選べない）
		if (status == ShelfStatus.STOCKED) {
			validation.add(invalidStatus());
		}
		throwIfInvalid(validation);

		if (repository.existsById(r.shelfNo())) {
			throw new LocationConflictException(LocationConflictException.Reason.DUPLICATE);
		}
		try {
			return repository.saveAndFlush(new Location(r.shelfNo(), status, normalize(r.remarks())));
		} catch (DataIntegrityViolationException e) {
			// 同時登録で先に入れられた場合
			throw new LocationConflictException(LocationConflictException.Reason.DUPLICATE);
		}
	}

	/**
	 * 棚番（要求の shelfNo が新しい棚番）・棚状況・備考を更新する。
	 * 在庫のある棚は棚番を変えられず、禁止棚にもできない（409 HAS_STOCK）。新しい棚番が既にあれば 409 DUPLICATE。
	 */
	@Transactional
	public Location update(String shelfNo, LocationRequest request) {
		LocationRequest r = request == null ? EMPTY : request;
		ValidationResult validation = new ValidationResult();
		InputRule.SHELF_NO.validate(validation, r.shelfNo());
		ShelfStatus status = validateStatus(validation, r.shelfStatus());
		InputRule.REMARKS.validate(validation, r.remarks());
		throwIfInvalid(validation);

		Location location = repository.findById(shelfNo).orElseThrow(() -> new LocationNotFoundException(shelfNo));
		// 実棚への変更は不可（実棚のままの更新、つまり備考だけの更新は可）
		if (status == ShelfStatus.STOCKED && location.getShelfStatus() != ShelfStatus.STOCKED) {
			validation.add(invalidStatus());
			throw new ValidationFailedException(validation);
		}
		boolean hasStock = location.getShelfStatus() == ShelfStatus.STOCKED || repository.countStockRows(shelfNo) > 0;
		boolean renumber = !shelfNo.equals(r.shelfNo());
		if (hasStock && (renumber || status != ShelfStatus.STOCKED)) {
			throw new LocationConflictException(LocationConflictException.Reason.HAS_STOCK);
		}
		if (renumber && repository.existsById(r.shelfNo())) {
			throw new LocationConflictException(LocationConflictException.Reason.DUPLICATE);
		}
		try {
			if (renumber) {
				int rows = repository.renumber(shelfNo, r.shelfNo(), status.label(), normalize(r.remarks()));
				if (rows == 0) {
					throw new LocationNotFoundException(shelfNo);
				}
				return new Location(r.shelfNo(), status, normalize(r.remarks()));
			}
			location.change(status, normalize(r.remarks()));
			return repository.saveAndFlush(location);
		} catch (DataIntegrityViolationException e) {
			throw conflictOf(e);
		} catch (OptimisticLockingFailureException | OptimisticLockException | org.hibernate.StaleStateException e) {
			// 確認の直後に他の人に削除された場合（更新対象の行がない）
			throw new LocationNotFoundException(shelfNo);
		}
	}

	/** 実棚（在庫がある棚）は削除できない。在庫がなく、棚状況が空棚・禁止棚なら削除できる。 */
	@Transactional
	public void delete(String shelfNo) {
		Location location = repository.findById(shelfNo).orElseThrow(() -> new LocationNotFoundException(shelfNo));
		if (location.getShelfStatus() == ShelfStatus.STOCKED || repository.countStockRows(shelfNo) > 0) {
			throw new LocationConflictException(LocationConflictException.Reason.HAS_STOCK);
		}
		try {
			repository.delete(location);
			repository.flush();
		} catch (DataIntegrityViolationException e) {
			// 確認の直後に入庫された場合（在庫の外部キー違反）
			throw new LocationConflictException(LocationConflictException.Reason.HAS_STOCK);
		} catch (OptimisticLockingFailureException | OptimisticLockException | org.hibernate.StaleStateException e) {
			// 確認の直後に他の人に削除された場合
			throw new LocationNotFoundException(shelfNo);
		}
	}

	/** 外部キー違反（在庫が参照している）なら HAS_STOCK、それ以外（主キーの重複）は DUPLICATE。 */
	private static LocationConflictException conflictOf(DataIntegrityViolationException e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t.getMessage() != null && t.getMessage().contains(STOCK_FK)) {
				return new LocationConflictException(LocationConflictException.Reason.HAS_STOCK);
			}
		}
		return new LocationConflictException(LocationConflictException.Reason.DUPLICATE);
	}

	/** 備考は空なら null で保存する。 */
	private static String normalize(String remarks) {
		return remarks == null || remarks.isEmpty() ? null : remarks;
	}

	/** 棚状況（定数名）を検証して返す。不正・未入力なら null を返しエラーを追加する。 */
	private static ShelfStatus validateStatus(ValidationResult validation, String shelfStatus) {
		if (shelfStatus == null || shelfStatus.isEmpty()) {
			validation.add(new ValidationError("shelfStatus", ErrorType.REQUIRED, "shelfStatus の検証エラー: REQUIRED"));
			return null;
		}
		try {
			return ShelfStatus.fromName(shelfStatus);
		} catch (IllegalArgumentException e) {
			validation.add(invalidStatus());
			return null;
		}
	}

	private static ValidationError invalidStatus() {
		return new ValidationError("shelfStatus", ErrorType.INVALID_FORMAT, "shelfStatus の検証エラー: INVALID_FORMAT");
	}

	private static void throwIfInvalid(ValidationResult validation) {
		if (!validation.isValid()) {
			throw new ValidationFailedException(validation);
		}
	}

}
