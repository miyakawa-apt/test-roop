-- 業務用テーブル（要件 TC-02）。アプリ起動時に Spring Boot 標準の初期化SQLとして毎回実行される。
-- すべて CREATE TABLE IF NOT EXISTS のため、既にあるテーブルとデータは壊さない（削除・再作成はしない）。
-- 形式の詳細（棚番 NN-NNN-NN など）はアプリ側検証に任せ、DB は桁数・範囲・NOT NULL・一意・外部キー・CHECK で守る。

-- ユーザー
CREATE TABLE IF NOT EXISTS app_user (
	user_id       VARCHAR(4)   NOT NULL,
	user_name     VARCHAR(15)  NOT NULL,
	password_hash VARCHAR(255) NOT NULL, -- ハッシュ値（平文 8〜14 文字より十分長い）
	role          VARCHAR(10)  NOT NULL,
	CONSTRAINT pk_app_user PRIMARY KEY (user_id),
	CONSTRAINT ck_app_user_id_len CHECK (char_length(user_id) = 4),
	CONSTRAINT ck_app_user_name_len CHECK (char_length(user_name) >= 1),
	CONSTRAINT ck_app_user_role CHECK (role IN ('管理者', '一般'))
);

-- 商品
CREATE TABLE IF NOT EXISTS product (
	product_code  VARCHAR(8)  NOT NULL,
	product_name  VARCHAR(30) NOT NULL,
	category      VARCHAR(10) NOT NULL,
	unit_price    INTEGER     NOT NULL,
	case_quantity INTEGER     NOT NULL,
	CONSTRAINT pk_product PRIMARY KEY (product_code),
	CONSTRAINT ck_product_code_len CHECK (char_length(product_code) = 8),
	CONSTRAINT ck_product_name_len CHECK (char_length(product_name) >= 1),
	CONSTRAINT ck_product_category CHECK (category IN ('食品', '日用品', '工具', '部材', 'その他')),
	CONSTRAINT ck_product_unit_price CHECK (unit_price BETWEEN 0 AND 9999999),
	CONSTRAINT ck_product_case_quantity CHECK (case_quantity BETWEEN 1 AND 9999)
);

-- ロケーション（棚番は 列2桁-連3桁-段2桁 = 9 文字）
CREATE TABLE IF NOT EXISTS location (
	shelf_no     VARCHAR(9)  NOT NULL,
	shelf_status VARCHAR(10) NOT NULL,
	remarks      VARCHAR(50),
	CONSTRAINT pk_location PRIMARY KEY (shelf_no),
	CONSTRAINT ck_location_shelf_no_len CHECK (char_length(shelf_no) = 9),
	CONSTRAINT ck_location_status CHECK (shelf_status IN ('空棚', '実棚', '禁止棚'))
);

-- 在庫（商品コード＋棚番で一意。入庫日を最初の日付のまま保つのはアプリ側の規則）
CREATE TABLE IF NOT EXISTS stock (
	stock_id      BIGINT GENERATED ALWAYS AS IDENTITY,
	product_code  VARCHAR(8)  NOT NULL,
	shelf_no      VARCHAR(9)  NOT NULL,
	quantity      INTEGER     NOT NULL,
	received_date DATE        NOT NULL,
	created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT pk_stock PRIMARY KEY (stock_id),
	CONSTRAINT uq_stock_product_shelf UNIQUE (product_code, shelf_no),
	CONSTRAINT fk_stock_product FOREIGN KEY (product_code) REFERENCES product (product_code),
	CONSTRAINT fk_stock_location FOREIGN KEY (shelf_no) REFERENCES location (shelf_no),
	CONSTRAINT ck_stock_quantity CHECK (quantity BETWEEN 0 AND 99999)
);

-- 実績（履歴。商品・棚が後で削除されても残すため、商品コード・棚番には外部キーを付けない）
CREATE TABLE IF NOT EXISTS work_result (
	result_id       BIGINT GENERATED ALWAYS AS IDENTITY,
	result_type     VARCHAR(10) NOT NULL,
	worked_at       TIMESTAMP   NOT NULL,
	product_code    VARCHAR(8)  NOT NULL,
	shelf_no        VARCHAR(9)  NOT NULL,
	quantity        INTEGER     NOT NULL,
	before_quantity INTEGER     NOT NULL, -- 変更前の在庫数（新規入庫なら 0）
	after_quantity  INTEGER     NOT NULL, -- 変更後の在庫数（在庫がなくなれば 0）
	note            VARCHAR(50),          -- 備考（入庫・出庫）／理由（修正・削除。必須）
	user_id         VARCHAR(4)  NOT NULL,
	created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT pk_work_result PRIMARY KEY (result_id),
	CONSTRAINT fk_work_result_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
	CONSTRAINT ck_work_result_type CHECK (result_type IN ('入庫', '出庫', '修正', '削除')),
	CONSTRAINT ck_work_result_product_code_len CHECK (char_length(product_code) = 8),
	CONSTRAINT ck_work_result_shelf_no_len CHECK (char_length(shelf_no) = 9),
	CONSTRAINT ck_work_result_quantity CHECK (quantity BETWEEN 1 AND 99999),
	CONSTRAINT ck_work_result_before CHECK (before_quantity BETWEEN 0 AND 99999),
	CONSTRAINT ck_work_result_after CHECK (after_quantity BETWEEN 0 AND 99999),
	CONSTRAINT ck_work_result_note CHECK (
		result_type IN ('入庫', '出庫')
		OR (note IS NOT NULL AND char_length(note) >= 1))
);
