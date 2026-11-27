package jp.co.apt.mony.common.code;

import jakarta.persistence.AttributeConverter;

/**
 * 区分値 enum と DB の日本語表示名（label）を相互に変換する JPA コンバーターの共通実装。
 * 使うときは enum ごとに継承して {@code @Converter} を付けたクラスを作り、
 * エンティティの項目に {@code @Convert(converter = ...)} で指定する。
 * DB の値が不正（定義外の表示名）なら IllegalArgumentException。null は null のまま変換する。
 */
public abstract class LabelConverter<E extends Enum<E> & Labeled> implements AttributeConverter<E, String> {

	private final Class<E> type;

	protected LabelConverter(Class<E> type) {
		this.type = type;
	}

	@Override
	public String convertToDatabaseColumn(E attribute) {
		return attribute == null ? null : attribute.label();
	}

	@Override
	public E convertToEntityAttribute(String dbData) {
		return dbData == null ? null : CodeSupport.parseLabel(type, dbData);
	}

}
