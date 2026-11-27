package jp.co.apt.mony.common.code;

import jakarta.persistence.Converter;

/** {@link ResultType} と DB の日本語表示名の JPA コンバーター。 */
@Converter
public class ResultTypeConverter extends LabelConverter<ResultType> {

	public ResultTypeConverter() {
		super(ResultType.class);
	}

}
