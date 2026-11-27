package jp.co.apt.mony.common.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoleLabelTest {

	@Test
	void DBの日本語値からRoleへ変換できる() {
		assertEquals(Role.ADMIN, Role.fromLabel("管理者"));
		assertEquals(Role.GENERAL, Role.fromLabel("一般"));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", " ", "ADMIN", "管理者 ", "その他" })
	void 不正な表示名は例外(String value) {
		assertThrows(IllegalArgumentException.class, () -> Role.fromLabel(value));
	}

	@Test
	void 権限名はROLE_ADMINとROLE_GENERAL() {
		assertEquals("ROLE_ADMIN", Role.ADMIN.authority());
		assertEquals("ROLE_GENERAL", Role.GENERAL.authority());
	}

}
