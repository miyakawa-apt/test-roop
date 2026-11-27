package jp.co.apt.mony.common.code;

import jakarta.persistence.Converter;

/** {@link ShelfStatus} と DB の日本語表示名の JPA コンバーター。 */
@Converter
public class ShelfStatusConverter extends LabelConverter<ShelfStatus> {

	public ShelfStatusConverter() {
		super(ShelfStatus.class);
	}

}
