package jp.co.apt.mony.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, String> {

	/** 商品コード順の一覧。 */
	List<Product> findAllByOrderByProductCodeAsc();

	/** その商品の在庫（stock）の行数。 */
	@Query(value = "SELECT count(*) FROM stock WHERE product_code = :code", nativeQuery = true)
	long countStockRows(@Param("code") String code);

}
