package jp.co.apt.mony.common.code;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CodeTest {

	@Test
	void 表示名は要件どおり() {
		assertArrayEquals(new String[] { "管理者", "一般" }, labels(Role.values()));
		assertArrayEquals(new String[] { "空棚", "実棚", "禁止棚" }, labels(ShelfStatus.values()));
		assertArrayEquals(new String[] { "入庫", "出庫", "修正", "削除" }, labels(ResultType.values()));
		assertArrayEquals(new String[] { "食品", "日用品", "工具", "部材", "その他" }, labels(Category.values()));
	}

	@Test
	void 文字列からenumへ変換できる() {
		assertEquals(Role.ADMIN, Role.fromName("ADMIN"));
		assertEquals(ShelfStatus.PROHIBITED, ShelfStatus.fromName("PROHIBITED"));
		assertEquals(ResultType.DELETION, ResultType.fromName("DELETION"));
		assertEquals(Category.DAILY_GOODS, Category.fromName("DAILY_GOODS"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "admin", "ADMIN ", "管理者", "UNKNOWN" })
	void 不正値はエラー(String value) {
		assertThrows(IllegalArgumentException.class, () -> Role.fromName(value));
	}

	@Test
	void nullはエラー() {
		assertThrows(IllegalArgumentException.class, () -> Category.fromName(null));
	}

	private static String[] labels(Object[] values) {
		return java.util.Arrays.stream(values).map(v -> v instanceof Role r ? r.label()
				: v instanceof ShelfStatus s ? s.label() : v instanceof ResultType t ? t.label()
						: ((Category) v).label()).toArray(String[]::new);
	}

}
