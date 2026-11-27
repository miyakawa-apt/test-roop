package jp.co.apt.mony.product;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jp.co.apt.mony.common.code.Category;
import jp.co.apt.mony.common.code.CategoryConverter;

/**
 * 商品（テーブル product）。商品コードは自分で決める主キーのため Persistable にして、
 * 新規かどうかを明示する（既存コードを save で上書きしてしまうのを防ぐ）。
 */
@Entity
@Table(name = "product")
public class Product implements Persistable<String> {

	@Id
	@Column(name = "product_code", length = 8, nullable = false, updatable = false)
	private String productCode;

	@Column(name = "product_name", length = 30, nullable = false)
	private String productName;

	@Convert(converter = CategoryConverter.class)
	@Column(name = "category", length = 10, nullable = false)
	private Category category;

	@Column(name = "unit_price", nullable = false)
	private int unitPrice;

	@Column(name = "case_quantity", nullable = false)
	private int caseQuantity;

	@Transient
	private boolean isNew = true;

	protected Product() {
	}

	public Product(String productCode, String productName, Category category, int unitPrice, int caseQuantity) {
		this.productCode = productCode;
		this.productName = productName;
		this.category = category;
		this.unitPrice = unitPrice;
		this.caseQuantity = caseQuantity;
	}

	/** 商品コード以外を書き換える（商品コードは変更不可）。 */
	public void change(String productName, Category category, int unitPrice, int caseQuantity) {
		this.productName = productName;
		this.category = category;
		this.unitPrice = unitPrice;
		this.caseQuantity = caseQuantity;
	}

	@Override
	public String getId() {
		return productCode;
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

	public String getProductCode() {
		return productCode;
	}

	public String getProductName() {
		return productName;
	}

	public Category getCategory() {
		return category;
	}

	public int getUnitPrice() {
		return unitPrice;
	}

	public int getCaseQuantity() {
		return caseQuantity;
	}

}
