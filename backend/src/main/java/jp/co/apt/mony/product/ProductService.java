package jp.co.apt.mony.product;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.co.apt.mony.common.code.Category;
import jp.co.apt.mony.common.validation.ErrorType;
import jp.co.apt.mony.common.validation.InputRule;
import jp.co.apt.mony.common.validation.ValidationError;
import jp.co.apt.mony.common.validation.ValidationFailedException;
import jp.co.apt.mony.common.validation.ValidationResult;

/** 商品マスタの業務処理（FR-07）。登録・更新・削除はそれぞれ1トランザクション。 */
@Service
public class ProductService {

	private static final ProductRequest EMPTY = new ProductRequest(null, null, null, null, null);

	private final ProductRepository repository;

	public ProductService(ProductRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public List<Product> list() {
		return repository.findAllByOrderByProductCodeAsc();
	}

	@Transactional
	public Product register(ProductRequest request) {
		ProductRequest r = request == null ? EMPTY : request;
		ValidationResult validation = new ValidationResult();
		InputRule.PRODUCT_CODE.validate(validation, r.productCode());
		validateBody(validation, r);
		throwIfInvalid(validation);

		if (repository.existsById(r.productCode())) {
			throw new ProductConflictException(ProductConflictException.Reason.DUPLICATE);
		}
		try {
			return repository.saveAndFlush(new Product(r.productCode(), r.productName(),
					Category.fromName(r.category()), Integer.parseInt(r.unitPrice()),
					Integer.parseInt(r.caseQuantity())));
		} catch (DataIntegrityViolationException e) {
			// 同時登録で先に入れられた場合
			throw new ProductConflictException(ProductConflictException.Reason.DUPLICATE);
		}
	}

	/** 商品コード以外を更新する（要求の productCode は無視）。 */
	@Transactional
	public Product update(String code, ProductRequest request) {
		ProductRequest r = request == null ? EMPTY : request;
		ValidationResult validation = new ValidationResult();
		validateBody(validation, r);
		throwIfInvalid(validation);

		Product product = repository.findById(code).orElseThrow(() -> new ProductNotFoundException(code));
		product.change(r.productName(), Category.fromName(r.category()), Integer.parseInt(r.unitPrice()),
				Integer.parseInt(r.caseQuantity()));
		return repository.saveAndFlush(product);
	}

	/** 在庫がある商品は削除できない。実績履歴が残っていても削除できる（実績は商品コードを文字列で持つだけ）。 */
	@Transactional
	public void delete(String code) {
		Product product = repository.findById(code).orElseThrow(() -> new ProductNotFoundException(code));
		if (repository.countStockRows(code) > 0) {
			throw new ProductConflictException(ProductConflictException.Reason.HAS_STOCK);
		}
		try {
			repository.delete(product);
			repository.flush();
		} catch (DataIntegrityViolationException e) {
			// 確認の直後に入庫された場合（在庫の外部キー違反）
			throw new ProductConflictException(ProductConflictException.Reason.HAS_STOCK);
		}
	}

	private static void validateBody(ValidationResult validation, ProductRequest r) {
		InputRule.PRODUCT_NAME.validate(validation, r.productName());
		validateCategory(validation, r.category());
		InputRule.UNIT_PRICE.validate(validation, r.unitPrice());
		InputRule.CASE_QUANTITY.validate(validation, r.caseQuantity());
	}

	private static void validateCategory(ValidationResult validation, String category) {
		if (category == null || category.isEmpty()) {
			validation.add(new ValidationError("category", ErrorType.REQUIRED, "category の検証エラー: REQUIRED"));
			return;
		}
		try {
			Category.fromName(category);
		} catch (IllegalArgumentException e) {
			validation.add(new ValidationError("category", ErrorType.INVALID_FORMAT,
					"category の検証エラー: INVALID_FORMAT"));
		}
	}

	private static void throwIfInvalid(ValidationResult validation) {
		if (!validation.isValid()) {
			throw new ValidationFailedException(validation);
		}
	}

}
