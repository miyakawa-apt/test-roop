package jp.co.apt.mony.common.code;

import jakarta.persistence.Converter;

/** {@link Role} と DB の日本語表示名の JPA コンバーター。 */
@Converter
public class RoleConverter extends LabelConverter<Role> {

	public RoleConverter() {
		super(Role.class);
	}

}
