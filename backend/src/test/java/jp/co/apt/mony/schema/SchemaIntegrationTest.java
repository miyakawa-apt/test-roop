package jp.co.apt.mony.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 実DB（PostgreSQL）で業務用5テーブルと制約を確かめる。
 * 環境変数 SPRING_DATASOURCE_PASSWORD があるときだけ実行する（DB がない環境では飛ばす）。
 * 各テストはロールバックされ、データを残さない。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "SPRING_DATASOURCE_PASSWORD", matches = ".+")
class SchemaIntegrationTest {

	@Autowired
	JdbcTemplate jdbc;

	private void insertProductAndLocation() {
		jdbc.update("INSERT INTO product VALUES ('00000001', 'テスト商品', '食品', 100, 12)");
		jdbc.update("INSERT INTO location (shelf_no, shelf_status) VALUES ('01-001-01', '空棚')");
	}

	private void insertStock() {
		jdbc.update("INSERT INTO stock (product_code, shelf_no, quantity, received_date) "
				+ "VALUES ('00000001', '01-001-01', 5, CURRENT_DATE)");
	}

	private void insertUser() {
		jdbc.update("INSERT INTO app_user VALUES ('ab12', 'テスト', 'hash', '一般')");
	}

	@Test
	void 業務用の5テーブルがある() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema()",
				String.class);
		assertThat(tables).contains("app_user", "product", "location", "stock", "work_result");
	}

	@Test
	void 在庫IDと実績IDは自動採番される() {
		insertProductAndLocation();
		insertStock();
		insertUser();
		jdbc.update("INSERT INTO work_result (result_type, worked_at, product_code, shelf_no, quantity, "
				+ "before_quantity, after_quantity, user_id) "
				+ "VALUES ('入庫', CURRENT_TIMESTAMP, '00000001', '01-001-01', 5, 0, 5, 'ab12')");
		assertThat(jdbc.queryForObject("SELECT stock_id FROM stock", Long.class)).isNotNull();
		assertThat(jdbc.queryForObject("SELECT result_id FROM work_result", Long.class)).isNotNull();
	}

	@Test
	void 在庫は商品コードと棚番で一意() {
		insertProductAndLocation();
		insertStock();
		assertThatThrownBy(this::insertStock).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 在庫は存在しない商品を参照できない() {
		jdbc.update("INSERT INTO location (shelf_no, shelf_status) VALUES ('01-001-01', '空棚')");
		assertThatThrownBy(this::insertStock).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 範囲外の単価は拒否される() {
		assertThatThrownBy(() -> jdbc.update("INSERT INTO product VALUES ('00000002', '商品', '食品', 10000000, 1)"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 区分値以外は拒否される() {
		assertThatThrownBy(
				() -> jdbc.update("INSERT INTO location (shelf_no, shelf_status) VALUES ('01-001-01', 'x')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void パスワード列はハッシュ値が入る長さ() {
		String hash = "$2a$10$" + "x".repeat(53); // BCrypt は 60 文字
		jdbc.update("INSERT INTO app_user VALUES ('ab12', 'テスト', ?, '管理者')", hash);
		assertThat(jdbc.queryForObject("SELECT length(password_hash) FROM app_user", Integer.class)).isEqualTo(60);
	}

	@Test
	void 実績は商品や棚がなくても残せる() {
		insertUser();
		jdbc.update("INSERT INTO work_result (result_type, worked_at, product_code, shelf_no, quantity, "
				+ "before_quantity, after_quantity, user_id) "
				+ "VALUES ('出庫', CURRENT_TIMESTAMP, '99999999', '99-999-99', 1, 1, 0, 'ab12')");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM work_result", Integer.class)).isEqualTo(1);
	}

	@Test
	void 修正と削除は理由が必須() {
		insertUser();
		assertThatThrownBy(() -> jdbc.update("INSERT INTO work_result (result_type, worked_at, product_code, "
				+ "shelf_no, quantity, before_quantity, after_quantity, user_id) "
				+ "VALUES ('修正', CURRENT_TIMESTAMP, '00000001', '01-001-01', 1, 1, 2, 'ab12')"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
