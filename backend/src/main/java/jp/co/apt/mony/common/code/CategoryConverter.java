package jp.co.apt.mony.common.code;

import jakarta.persistence.Converter;

/** {@link Category} と DB の日本語表示名の JPA コンバーター。 */
@Converter
public class CategoryConverter extends LabelConverter<Category> {

	public CategoryConverter() {
		super(Category.class);
	}

}
