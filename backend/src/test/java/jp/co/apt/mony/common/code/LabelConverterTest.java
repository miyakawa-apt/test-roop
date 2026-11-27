package jp.co.apt.mony.common.code;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 区分値 enum ⇔ DB の日本語表示名（label）の JPA コンバーター。 */
class LabelConverterTest {

	private final CategoryConverter category = new CategoryConverter();

	@Test
	void カテゴリは表示名でDBへ保存され_表示名から戻せる() {
		for (Category c : Category.values()) {
			String db = category.convertToDatabaseColumn(c);
			assertEquals(c.label(), db);
			assertEquals(c, category.convertToEntityAttribute(db));
		}
		assertEquals("日用品", category.convertToDatabaseColumn(Category.DAILY_GOODS));
		assertEquals(Category.PARTS, category.convertToEntityAttribute("部材"));
	}

	@Test
	void 他の区分値も表示名で往復できる() {
		RoleConverter role = new RoleConverter();
		ShelfStatusConverter shelf = new ShelfStatusConverter();
		ResultTypeConverter result = new ResultTypeConverter();
		for (Role r : Role.values()) {
			assertEquals(r, role.convertToEntityAttribute(role.convertToDatabaseColumn(r)));
		}
		for (ShelfStatus s : ShelfStatus.values()) {
			assertEquals(s, shelf.convertToEntityAttribute(shelf.convertToDatabaseColumn(s)));
		}
		for (ResultType t : ResultType.values()) {
			assertEquals(t, result.convertToEntityAttribute(result.convertToDatabaseColumn(t)));
		}
		assertEquals("管理者", role.convertToDatabaseColumn(Role.ADMIN));
		assertEquals("禁止棚", shelf.convertToDatabaseColumn(ShelfStatus.PROHIBITED));
		assertEquals("入庫", result.convertToDatabaseColumn(ResultType.RECEIPT));
	}

	@Test
	void nullはnullのまま() {
		assertNull(category.convertToDatabaseColumn(null));
		assertNull(category.convertToEntityAttribute(null));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", " ", "FOOD", "食品 ", "管理者" })
	void fromLabelは不正な表示名を拒否する(String value) {
		assertThrows(IllegalArgumentException.class, () -> Category.fromLabel(value));
	}

	@Test
	void DBの値が不正ならコンバーターは例外() {
		assertThrows(IllegalArgumentException.class, () -> category.convertToEntityAttribute("不明"));
	}

	@Test
	void fromLabelは4つの区分値で使える() {
		assertEquals(ShelfStatus.EMPTY, ShelfStatus.fromLabel("空棚"));
		assertEquals(ResultType.DELETION, ResultType.fromLabel("削除"));
		assertEquals(Category.OTHER, Category.fromLabel("その他"));
		assertEquals(Role.GENERAL, Role.fromLabel("一般"));
	}

}
