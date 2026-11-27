package jp.co.apt.mony.location;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, String> {

	/** 棚番順の一覧。 */
	List<Location> findAllByOrderByShelfNoAsc();

	/** その棚の在庫（stock）の行数。 */
	@Query(value = "SELECT count(*) FROM stock WHERE shelf_no = :shelfNo", nativeQuery = true)
	long countStockRows(@Param("shelfNo") String shelfNo);

	/**
	 * 棚番（主キー）を含めて更新する。更新した行数を返す（0 なら旧棚番の棚がない）。
	 * 在庫（stock）が参照している棚は外部キーにより更新できない。
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = "UPDATE location SET shelf_no = :newNo, shelf_status = :status, remarks = :remarks "
			+ "WHERE shelf_no = :oldNo", nativeQuery = true)
	int renumber(@Param("oldNo") String oldNo, @Param("newNo") String newNo, @Param("status") String status,
			@Param("remarks") String remarks);

}
