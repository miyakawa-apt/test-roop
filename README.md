# Mony（ミニWMS）

要件定義書に基づくミニWMSのWebアプリです。

## 前提

- Java 25（JDK）、Node.js 22 以上（確認時は v26.13.1 / npm 11.20.0）、Docker（Docker Compose）
- Maven は不要（`backend/mvnw` が取得します）

## 構成

- `docker-compose.yml` : 開発用 PostgreSQL 17
- `backend/` : Spring Boot バックエンド（Maven Wrapper 付き）
- `frontend/` : React フロントエンド（Vite + TypeScript）

## 手順（コマンドのみ）

```
# 1. DBパスワードの環境変数をセット（値は開発用ダミー。Compose 用とバックエンド用に同じ値を入れる）
#    bash（Git Bash 等）
export MONY_DB_PASSWORD=mony_dev_password
export SPRING_DATASOURCE_PASSWORD=mony_dev_password
#    PowerShell
$env:MONY_DB_PASSWORD = "mony_dev_password"
$env:SPRING_DATASOURCE_PASSWORD = "mony_dev_password"

# 2. 開発用DBの起動（リポジトリのルートで実行）
docker compose up -d --wait

# 3. バックエンドのビルド・テスト（DB不要。Windows は mvnw.cmd）
cd backend
./mvnw -B verify

# 4. バックエンドの起動（DBが起動し、手順1の `SPRING_DATASOURCE_PASSWORD` がセットされていること）
./mvnw spring-boot:run

# 5. ヘルスチェック（別ターミナル。DB接続も確認し、200 と {"status":"UP","database":"UP"} が返る）
curl -i http://localhost:8080/api/health

# 6. フロントエンドのインストール・ビルド・テスト
cd ../frontend
npm ci
npm run build
npm test

# 7. フロントエンドの起動（http://localhost:5173 。/api はバックエンドへ中継）
npm run dev

# 8. 開発用DBの停止（データは残す）
docker compose down
```

### 設定の上書き（環境変数）

| 環境変数 | 既定値 | 用途 |
| --- | --- | --- |
| `MONY_DB_PORT` | `5432` | Compose が公開するDBのホスト側ポート |
| `MONY_DB_NAME` / `MONY_DB_USER` | `mony` / `mony` | Compose のDB設定 |
| `MONY_DB_PASSWORD` | 開発用ダミー値 | Compose のDBパスワード |
| `SPRING_DATASOURCE_PASSWORD` | なし（バックエンド起動時は必須） | バックエンドのDBパスワード（Spring Boot 標準）。`MONY_DB_PASSWORD` と同じ値にする |
| `MONY_DB_URL` | `jdbc:postgresql://localhost:5432/mony` | バックエンドのDB接続URL |
| `MONY_SERVER_PORT` | `8080` | バックエンドの待受ポート |
| `MONY_API_TARGET` | `http://localhost:8080` | フロント開発サーバーの `/api` 中継先 |

バックエンドのDBユーザーも `MONY_DB_USER` で上書きできます。バックエンドのパスワードは `SPRING_DATASOURCE_PASSWORD` に Compose の `MONY_DB_PASSWORD` と同じ値を必ず設定してください（未設定だとバックエンドは起動しません。ビルド・テストには不要です）。
ホストの 5432 が使用中の場合は、例として `MONY_DB_PORT=55432` で Compose を起動し、バックエンドを `MONY_DB_URL=jdbc:postgresql://localhost:55432/mony` で起動します。

### 補足

- バックエンドのテストは DB なしで通ります（ヘルスチェックの WebMvcTest。DB は差し替え）。DB を使う確認は手順 4〜5 で行います。
- Spring Security は入れていますが、ログイン機能は未実装です。`/api/health`・トップ・静的ファイル以外は認証が必要（401）です。
- Spring Session（JDBC）のテーブルは `spring.session.jdbc.initialize-schema=always` で起動時に作成されます。業務テーブルのスキーマ管理は今後の課題です。

## 採用した版と確認結果（2026年11月27日）

| 部品 | 版 | 確認結果 |
| --- | --- | --- |
| Java | 25（Temurin 25.0.5） | ビルド・起動とも成功 |
| Spring Boot | 4.1.3（4.2 系へは上げない） | `./mvnw -B verify` 成功（テスト2件） |
| Maven（Wrapper 経由） | 3.10.2 | `./mvnw -v` で確認 |
| PostgreSQL | 17（イメージ `postgres:17`、確認時 17.11） | `docker compose up -d --wait` で healthy |
| PostgreSQL JDBC | Spring Boot 4.1.3 の管理版 | 接続し `/api/health` が 200 |
| React / React DOM | 19.3.1 | ビルド・テスト成功 |
| React Router | 7.18.7 | トップ画面のルーティングで使用 |
| Vite | 8.4.0 | `npm run build` 成功 |
| Tailwind CSS | 4.3.5（`@tailwindcss/vite`） | ビルド成功 |
| TypeScript | 7.0.2 | `tsc --noEmit` 成功 |
| Vitest / Testing Library | 5.1.2 / 16.3.3 | `npm test` 1件成功 |

動作確認の結果: バックエンドを DB に接続して起動し、`GET /api/health` が 200（`database: UP`）を返しました。DB 停止中は 503（`database: DOWN`）を返し、認証が必要なパスは 401 でした。

## 区分値と入力ルール（DT-01）

バックエンドの検証部品です（`jp.co.apt.mony.common.code`／`jp.co.apt.mony.common.validation`。純Javaで依存追加なし）。フロントエンドでも同じ範囲を使ってください。

### 区分値（表示名）

| 区分 | 定数名 : 表示名 |
| --- | --- |
| 権限（`Role`） | `ADMIN` : 管理者 / `GENERAL` : 一般 |
| 棚状況（`ShelfStatus`） | `EMPTY` : 空棚 / `STOCKED` : 実棚 / `PROHIBITED` : 禁止棚 |
| 実績区分（`ResultType`） | `RECEIPT` : 入庫 / `ISSUE` : 出庫 / `CORRECTION` : 修正 / `DELETION` : 削除 |
| カテゴリ（`Category`） | `FOOD` : 食品 / `DAILY_GOODS` : 日用品 / `TOOL` : 工具 / `PARTS` : 部材 / `OTHER` : その他 |

`fromName("定数名")` で文字列から変換します（不正値・null は `IllegalArgumentException`）。

### 入力ルール一覧（`InputRule`）

| 項目 | 項目名（識別子） | ルール |
| --- | --- | --- |
| 数量（入庫・出庫・修正後） | `quantity` | 数字のみ 1〜99,999 |
| 単価 | `unitPrice` | 数字のみ 0〜9,999,999 |
| ケース入数 | `caseQuantity` | 数字のみ 1〜9,999 |
| 棚番 | `shelfNo` | `NN-NNN-NN`（2桁-3桁-2桁、数字のみ） |
| 商品コード | `productCode` | 数字のみ 8桁 |
| 商品名 | `productName` | 1〜30文字 |
| ユーザー名 | `userName` | 1〜15文字 |
| ユーザーID | `userId` | 半角英数 4文字 |
| パスワード | `password` | 8〜14文字 |
| 備考 | `remarks` | 0〜50文字（空・null可） |
| 理由 | `reason` | 1〜50文字 |

エラー種別（`ErrorType`）は `REQUIRED`（未入力）／`INVALID_FORMAT`（形式不正）／`OUT_OF_RANGE`（数値の範囲外）／`INVALID_LENGTH`（文字数の範囲外）で、`ValidationResult` が項目名と種別を複数件まとめて保持します（文言は後続の共通UI要件で決定）。

判断事項:

- 文字数は文字（コードポイント）数で数えます（サロゲートペアは1文字）。
- 値は補正しません。全角数字・前後の空白・改行を含む値は「数字のみ」「半角英数」ルールでは不正（`INVALID_FORMAT`）、文字数ルールでは1文字として数えます。
- 数字のみの項目で先頭 0 は許容します（例: `00001` は数量1として有効）。
