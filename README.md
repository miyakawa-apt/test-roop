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
| `MONY_BOOTSTRAP_ADMIN_PASSWORD` | なし（任意） | 最初の管理者（ユーザーID `A001`）のパスワード（8〜14文字）。設定されていて `app_user` が 0 件のときだけ、起動時にハッシュ化して登録する（下記「ログインと権限制御」） |
| `MONY_API_TARGET` | `http://localhost:8080` | フロント開発サーバーの `/api` 中継先 |

バックエンドのDBユーザーも `MONY_DB_USER` で上書きできます。バックエンドのパスワードは `SPRING_DATASOURCE_PASSWORD` に Compose の `MONY_DB_PASSWORD` と同じ値を必ず設定してください（未設定だとバックエンドは起動しません。ビルド・テストには不要です）。
ホストの 5432 が使用中の場合は、例として `MONY_DB_PORT=55432` で Compose を起動し、バックエンドを `MONY_DB_URL=jdbc:postgresql://localhost:55432/mony` で起動します。

### 補足

- バックエンドのテストは DB なしで通ります（ヘルスチェックの WebMvcTest は DB を差し替え。DB が必要なテストは環境変数 `SPRING_DATASOURCE_PASSWORD` がないと飛ばされます）。DB を使う確認は手順 4〜5 と、下記「DB を使うテストの実行」で行います。
- ログインAPIと権限制御があります（下記「ログインと権限制御」）。`/api/health`・ログインAPI・トップ・静的ファイル以外は、ログインしていないと 401 です。
- Spring Session（JDBC）のテーブルは `spring.session.jdbc.initialize-schema=always` で起動時に作成されます。業務テーブルは下記「業務用テーブル」のとおり `schema.sql` で作成します（セッション用テーブルとは独立）。

## 業務用テーブル

アプリ起動時に `backend/src/main/resources/schema.sql`（`spring.sql.init.mode=always`）が実行され、無いテーブルだけを `CREATE TABLE IF NOT EXISTS` で作ります。既にあるテーブルとデータは壊さず、削除・再作成や初期データの登録は行いません。

| テーブル | 内容 | 主なキー・制約 |
| --- | --- | --- |
| `app_user` | ユーザー（ユーザーID／ユーザー名／パスワード（ハッシュ値）／権限） | 主キー `user_id`。権限は 管理者／一般。パスワード列は 255 文字（ハッシュ値用） |
| `product` | 商品（商品コード／商品名／カテゴリ／単価／ケース入数） | 主キー `product_code`。カテゴリは 食品／日用品／工具／部材／その他。単価 0〜9,999,999、ケース入数 1〜9,999 |
| `location` | ロケーション（棚番／棚状況／備考） | 主キー `shelf_no`（9 文字）。棚状況は 空棚／実棚／禁止棚。備考は 50 文字まで（NULL 可） |
| `stock` | 在庫（在庫ID／商品コード／棚番／在庫数／入庫日／登録・更新日時） | 主キー `stock_id`（自動採番）。商品コード＋棚番で一意。商品・棚への外部キーあり。在庫数 0〜99,999 |
| `work_result` | 実績（実績ID／区分／作業日時／商品コード／棚番／数量／変更前後の在庫数／備考・理由／ユーザーID／登録日時） | 主キー `result_id`（自動採番）。区分は 入庫／出庫／修正／削除。数量 1〜99,999。ユーザーへの外部キーあり |

設計上の判断:

- 区分値の列は日本語の文字列値のまま保存し、`CHECK` で許可する値に限ります。
- DB は桁数・範囲・NOT NULL・一意・外部キー・CHECK で守ります。棚番の形式（`NN-NNN-NN`）や商品コードの数字 8 桁などの形式検査はアプリ側（別イシュー）で行い、DB では長さのみ確認します。
- 実績の変更前・変更後の在庫数は NULL を許さず、入庫で新規なら変更前を 0、在庫がなくなれば変更後を 0 とします。備考・理由は 1 列（`note`、NULL 可）で、区分が 修正／削除 のときだけ必須（1〜50 文字）です。
- 実績の商品コード・棚番には外部キーを付けません（商品・棚が後で削除されても履歴を残すため）。在庫の商品・棚は削除不可とし、その規則はアプリ側で扱います。
- 在庫数は 0 を許します（0〜99,999）。在庫が 0 になった行を残すか消すかはアプリ側で決めます。
- 入庫日を最初の日付のまま保つ（再入庫で更新しない）のはアプリ側の規則です。

### DB を使うテストの実行

DB を起動し（手順 2）、環境変数を指定して `./mvnw -B verify` を実行すると、実 DB で 5 テーブルの作成と一意制約などを確かめるテスト（`SchemaIntegrationTest`、9 件）が実行されます。環境変数 `SPRING_DATASOURCE_PASSWORD` が無いとこのテストは飛ばされます。各テストはロールバックされ、データを残しません。

```
# bash（Git Bash 等。DB のポートを変えた場合は MONY_DB_URL も合わせる）
export SPRING_DATASOURCE_PASSWORD=mony_dev_password
cd backend
./mvnw -B verify
```

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

## ログインと権限制御（FR-01-a）

ログイン状態は HTTP セッションで持ち、セッションは Spring Session JDBC で DB（`spring_session` テーブル）に保存します。本人確認は `app_user` の登録内容と BCrypt ハッシュの一致で行い、パスワードの平文は保存・出力・ログ出力しません。画面（React 側）とユーザーの登録・更新・削除 API は別イシューです。

### 権限

| DB の値（`app_user.role`） | `Role` | Spring Security の権限 | 入れる範囲 |
| --- | --- | --- | --- |
| 管理者 | `ADMIN` | `ROLE_ADMIN` | すべての API（`/api/admin/**` を含む） |
| 一般 | `GENERAL` | `ROLE_GENERAL` | `/api/admin/**` 以外のログイン後の API |

DB の日本語値から `Role` への変換は `Role.fromLabel("管理者")`、権限名は `Role.authority()` です。

アクセス制御:

- 認証なしで使える: `/api/health`、`POST /api/auth/login`、トップ・静的ファイル
- それ以外の `/api/**` は未ログインだと 401
- `/api/admin/**` は管理者のみ。一般ユーザーは 403（未ログインは 401）

### API（JSON）

| メソッド・パス | 内容 |
| --- | --- |
| `POST /api/auth/login` | ログイン |
| `POST /api/auth/logout` | ログアウト（セッションを破棄） |
| `GET /api/auth/me` | 現在のログイン者の取得 |

ログイン要求（`Content-Type: application/json`。ユーザーIDは半角英数4文字、パスワードは8〜14文字で、どちらも必須。値は補正しません）:

```
{ "userId": "A001", "password": "（入力値）" }
```

成功（200）とログイン者の取得（`GET /api/auth/me`、200）は同じ形です。`role` は定数名（`ADMIN` / `GENERAL`）、`roleLabel` は表示名です。セッションの Cookie（`SESSION`、HttpOnly・SameSite=Lax）が返ります。

```
{ "userId": "A001", "userName": "管理者", "role": "ADMIN", "roleLabel": "管理者" }
```

エラー:

| 状況 | ステータス | 本文 |
| --- | --- | --- |
| 入力検証エラー（未入力・形式・文字数。複数項目をまとめて返す。本文なしも同じ） | 400 | `{ "errors": [ { "field": "userId", "type": "INVALID_FORMAT" }, { "field": "password", "type": "REQUIRED" } ] }`（`type` は `REQUIRED` / `INVALID_FORMAT` / `INVALID_LENGTH`） |
| ユーザーIDまたはパスワードの不一致（ID が存在しない場合も同じ応答） | 401 | `{ "error": "AUTHENTICATION_FAILED" }` |
| 未ログインで保護された API を呼んだ | 401 | 本文なし |
| 権限不足（一般ユーザーが `/api/admin/**`） | 403 | （標準のエラー本文） |
| CSRF トークンなし・不一致の POST など | 403 | （標準のエラー本文） |

ログイン成功時、既存のセッションがあればセッションIDを変更します（セッション固定化攻撃対策）。ログアウトは成功時に 204 を返し、未ログインでも 204 です。

### CSRF 対策（Cookie 方式）

CSRF 保護は有効のままです（ログインAPIも対象）。方式は `CookieCsrfTokenRepository.withHttpOnlyFalse()` ＋ SPA 向けの `CsrfTokenRequestAttributeHandler`（BREACH 対策の XOR は使わず、Cookie の値をそのまま使う）です。Spring Security 7 ではトークンが遅延読み込みで、使うまで Cookie が書かれないため、`CsrfCookieFilter` で毎リクエスト読み込み、GET を含むすべての応答で `XSRF-TOKEN` Cookie を返します。

画面側の使い方:

1. 最初に任意の GET（例: `GET /api/health`）を呼ぶ。応答の `XSRF-TOKEN` Cookie（JavaScript から読める）にトークンが入る。
2. POST・PUT・DELETE などの変更系リクエストでは、その Cookie の値を `X-XSRF-TOKEN` ヘッダーに載せる（`fetch` では `credentials: "same-origin"` で Cookie も送る）。
3. ログアウトの直後は `XSRF-TOKEN` Cookie が消えるため、次に変更系リクエストを送る前に、もう一度 GET を呼んで Cookie を得る。

```
# curl での例（Cookie ファイルに保存して使う）
curl -c jar.txt -b jar.txt http://localhost:8080/api/health
curl -c jar.txt -b jar.txt -H "X-XSRF-TOKEN: <jar.txt の XSRF-TOKEN の値>" -H "Content-Type: application/json" \
  -d '{"userId":"A001","password":"（入力値）"}' http://localhost:8080/api/auth/login
curl -c jar.txt -b jar.txt http://localhost:8080/api/auth/me
```

### 最初の管理者

ユーザー登録 API はまだないため、環境変数 `MONY_BOOTSTRAP_ADMIN_PASSWORD` が設定されていて、かつ `app_user` が 0 件のときだけ、起動時にユーザーID `A001`・氏名「管理者」・権限 管理者 を BCrypt ハッシュで登録します。ユーザーが 1 件でもいれば何もしません。値は 8〜14 文字（入力ルールのパスワードと同じ）で、外れていると登録せず警告のみをログに出します。パスワードの値はログにも出しません。値はファイルに書かず、環境変数で渡してください。

```
# bash（Git Bash 等。値は各自で決める）
export MONY_BOOTSTRAP_ADMIN_PASSWORD='（8〜14文字の値）'
./mvnw spring-boot:run
```

### テスト

DB なしでは、ログイン・検証エラー・未ログイン 401・権限 403・ログアウト・CSRF などを WebMvcTest（`AuthControllerTest`）で、初期管理者の登録条件を単体テスト（`AdminBootstrapTest`）で確かめます。DB が必要な `AuthIntegrationTest`（セッションの `spring_session` 保存・ログアウトでの削除・セッションID変更など）と `AdminBootstrapIntegrationTest` は、環境変数 `SPRING_DATASOURCE_PASSWORD` があるときだけ実行されます。管理者用 API の確認に使うエンドポイントは `src/test` にあるテスト専用のもので、本番コードにはありません。

## 画面側の入力ルール検証（DT-01・フロントエンド）

`frontend/src/validation/`（`inputRules.ts`）と `frontend/src/codes.ts` です。サーバー側（`InputRule` など）と同じ項目名・範囲・エラー種別で、文言は持ちません（共通UI部品が項目名とエラー種別から組み立てます）。値は補正せず（全角→半角・空白除去なし）、文字数はコードポイント数で数えます。

```ts
import { validateForm, validateValue, checkRule } from './validation'

checkRule('quantity', '0')            // 'OUT_OF_RANGE'（問題なければ undefined）
validateValue('shelfNo', '01-001-01').isValid()  // true
const result = validateForm({ quantity: '０', productCode: '', remarks: 'メモ' })
result.errors()   // [{ field: 'quantity', type: 'INVALID_FORMAT' }, { field: 'productCode', type: 'REQUIRED' }]
result.typesOf('quantity')  // ['INVALID_FORMAT']
```

- `checkRule(規則名, 値)` : エラー種別（`REQUIRED` / `INVALID_FORMAT` / `OUT_OF_RANGE` / `INVALID_LENGTH`）か `undefined`。`null`・`undefined` は空として扱う
- `validateValue(規則名, 値)` : 単独の値を検証して `ValidationResult` を返す
- `validateRule(result, 規則名, 値, 項目名?)` : 既存の `result` に追加（項目名を省略すると既定の項目名。同じ規則を別の項目名で使えます）
- `validateForm({ 規則名: 値, ... })` : 複数項目をまとめて検証（キーが項目名）
- `ValidationResult` : `isValid()` / `errors()`（`{ field, type }` の配列）/ `typesOf(項目名)`
- `INPUT_RULES` : 規則の定義（範囲・形式）。区分値は `codes.ts` の `*_LABELS`（定数名→表示名）、`*_OPTIONS`（選択肢）、`isRole` 等（定数名の判定）

### サーバー側との一致一覧

| 項目 | 規則名（項目名） | 画面側の範囲（サーバー側と同じ） | エラー種別 |
| --- | --- | --- | --- |
| 数量 | `quantity` | 数字のみ 1〜99,999 | REQUIRED / INVALID_FORMAT / OUT_OF_RANGE |
| 単価 | `unitPrice` | 数字のみ 0〜9,999,999 | 同上 |
| ケース入数 | `caseQuantity` | 数字のみ 1〜9,999 | 同上 |
| 棚番 | `shelfNo` | `NN-NNN-NN`（数字のみ） | REQUIRED / INVALID_FORMAT |
| 商品コード | `productCode` | 数字のみ 8桁 | 同上 |
| 商品名 | `productName` | 1〜30文字 | REQUIRED / INVALID_LENGTH |
| ユーザー名 | `userName` | 1〜15文字 | 同上 |
| ユーザーID | `userId` | 半角英数 4文字 | REQUIRED / INVALID_FORMAT |
| パスワード | `password` | 8〜14文字 | REQUIRED / INVALID_LENGTH |
| 備考 | `remarks` | 0〜50文字（空・null可） | INVALID_LENGTH |
| 理由 | `reason` | 1〜50文字 | REQUIRED / INVALID_LENGTH |

数字のみの項目は先頭 0 を許容し（`00001` は有効）、18桁を超える数字は範囲外です。区分値の表示名（権限・棚状況・実績区分・カテゴリ）もサーバー側の定数名・表示名と同じです。判定はサーバー側 `InputRuleTest` のケースに揃えたテスト（`validation/inputRules.test.ts`、`codes.test.ts`）で確認します。

## 画面の共通部品（UI-02）

`frontend/src/components/` と `frontend/src/messages.ts` にあります。業務画面から次のように使います。

| 部品 | 使い方 |
| --- | --- |
| `ConfirmDialog` | `<ConfirmDialog message={confirmMessages.register} onYes={…} onNo={…} />`。登録・更新・削除の前に条件付きで描画する。Enter=はい、Esc=いいえ、既定フォーカスは「はい」。キーは document で受けるので、フォーカスが外れても効く（押しっぱなしは無視）。 |
| `ErrorDialog` | `<ErrorDialog messages={string[]} onClose={…} />`。複数エラーをまとめて表示。Enter か Esc で閉じる（`role="alertdialog"`）。 |
| `NoticeProvider` / `useNotice()` | 表示中の画面（ページ）の中に `NoticeProvider` を置き、`const { show, clear } = useNotice()`。`show(resultMessages.registered)` で画面下に緑の帯を出し、7.5秒で消える（表示中に再度呼ぶと置き換えて数え直す）。画面遷移で Provider が外れれば消える。遷移前に消したいときは `clear()`。 |
| `NotFound` | `<NotFound name="商品" />` →「該当する商品がありません。」（文面は `notFoundMessage(name)`） |
| `CountLabel` | `<CountLabel count={n} />` → 一覧右上に「N件」 |
| `TextInput` | `maxLength`（最大文字数）が必須の props。付け忘れると型エラーになる。`maxLength` は UTF-16 のコード単位で数えるため、サロゲートペア文字（一部の絵文字・異体字）は 1 文字が 2 と数えられる。 |
| `useSubmitValidation(validate, onValid)` | 入力チェックは Enter キーとボタン押下のときだけ。blur では検証しない（blur 用のハンドラは提供しない）。IME の変換確定 Enter（isComposing / keyCode 229）では検証せず、textarea の Enter（改行）は潰さない。`{ errors, submit, onEnterKey, clearErrors }` を返し、`submit` をボタンに、`onEnterKey` を入力欄の `onKeyDown` に渡す。 |

メッセージ文面は `messages.ts` に集約し、分類ごとに文末を揃えます。要件の2形式（「〜です。」「〜してください。」）は通知文・エラー文の書き方で、結果通知は「登録済みです。」のような「〜です。」（`resultMessages`・`notFoundMessage`）、エラーは「〜してください。」（`errorMessages`）にしました。確認はダイアログの問いかけなので、疑問形「〜します。よろしいですか？」のままにしています（`confirmMessages`）。

## ログイン画面・メニュー画面・共通ヘッダー（FR-01-b・UI-01）

### 画面一覧とルート

| 画面 | ルート | 内容 | 入れる権限 |
| --- | --- | --- | --- |
| S01 ログイン | `/login` | ユーザーID・パスワード（伏せ字）。検証は Enter キー・ボタン押下のときだけ | 誰でも（ログイン済みなら `/menu` へ） |
| S02 メニュー | `/menu`（`/` も `/menu` へ） | ログイン者を「ID : A001 (管理者)」形式で表示。ログイン／ログアウトは1つのボタンで兼用 | 誰でも |
| S03 入庫 | `/stock-in` | 仮ページ（「準備中です。」） | 管理者・一般 |
| S04 出庫 | `/stock-out` | 仮ページ | 管理者・一般 |
| S05 在庫リスト | `/stock-list` | 仮ページ | 管理者・一般 |
| S06 実績照会 | `/results` | 仮ページ | 管理者・一般 |
| S07 在庫修正 | `/stock-edit` | 仮ページ | 管理者のみ |
| S08 商品メンテナンス | `/product-admin` | 商品マスタの登録・更新・削除（下記「商品メンテナンス」） | 管理者のみ |
| S09 ロケーションメンテ | `/location-admin` | 仮ページ | 管理者のみ |
| S10 ユーザー管理 | `/user-admin` | 仮ページ | 管理者のみ |

全画面の上に共通ヘッダー（ログイン者「A001（管理者）」または「未ログイン」と、現在日時 `yyyy年MM月dd日 HH:mm:ss`。1秒ごとに更新）を出します。画面の定義は `frontend/src/screens.ts` です。

### 権限表（メニューの押せる／押せない）とガード

| 状態 | 業務メニュー（S03〜S06） | 管理者メニュー（S07〜S10） | ボタン |
| --- | --- | --- | --- |
| 未ログイン | 押せない（disabled） | 押せない | ログイン（S01 へ） |
| 一般 | 押せる | 押せない | ログアウト |
| 管理者 | 押せる | 押せる | ログアウト |

- ログアウトは `POST /api/auth/logout` のあと、`/menu` に留まったまま未ログイン状態になる。
- 未ログインで S03〜S10 を直接開くと `/login` へ。一般が S07〜S10 を直接開くと `/menu` へ戻す（メッセージは出さない）。未定義のパスは `/menu` へ。
- 起動時・再読み込み後は `GET /api/auth/me` でログイン状態を復元する。復元中は何も描画しない。
- パスワードは送信後に state から消し、`localStorage` / `sessionStorage` などには保存しない。不一致（401）はエラーダイアログを出し、パスワードだけクリアする。

### ログイン状態（`useAuth`）

`App` が `AuthProvider` を持つので、画面側は `useAuth()` で使います（`frontend/src/auth/AuthContext.tsx`）。

```ts
const { user, loading, login, logout } = useAuth()
// user: { userId, userName, role: 'ADMIN' | 'GENERAL', roleLabel } | null（未ログイン）
// loading: 起動時の復元中は true
const result = await login('A001', 入力値) // { type: 'success' | 'invalid' | 'mismatch' | 'error', ... }
await logout()
```

ルートの権限ガードは `auth/RequireAuth.tsx`（`<RequireAuth adminOnly />` の下に Route を並べる）です。

### API クライアント（`frontend/src/api/`）

```ts
import { apiGet, apiPost, apiSend } from './api/client'

const res = await apiGet<Product[]>('/api/products')   // { status, ok, body }
const r2 = await apiPost('/api/auth/login', { userId, password })
await apiSend('PUT', '/api/xxx/1', { ... })             // POST / PUT / DELETE
```

- `fetch` を `credentials: 'same-origin'`（SESSION Cookie を送る）で呼び、JSON を読み書きする。
- 変更系（POST・PUT・DELETE）は `XSRF-TOKEN` Cookie を読んで `X-XSRF-TOKEN` ヘッダーを自動で付ける。Cookie が無いとき（初回・ログアウト直後）は先に `GET /api/health` で取得する。
- HTTP エラー（400・401 など）は例外にせず `status` と `body` で返す。通信失敗だけが例外になる。
- ログイン関連は `api/auth.ts`（`fetchMe` / `postLogin` / `postLogout`）。

### 共通メッセージ（`messages.ts`）

`validationMessage(項目名, エラー種別)` が検証結果から「〜してください。」形の文面を作ります（例: `userId` ＋ `REQUIRED` →「ユーザーIDを入力してください。」）。項目の表示名は `fieldLabels`、ログイン不一致の文言は `loginMessages.mismatch` です。

## 商品メンテナンス（FR-07・S08）

管理者だけが使える商品マスタの画面とAPIです。商品コード（8桁数字・重複不可）、商品名（1〜30文字）、カテゴリ（食品／日用品／工具／部材／その他）、単価（0〜9,999,999）、ケース入数（1〜9,999）を扱います。入力ルールは画面側とサーバー側で同じ（`InputRule` ／ `validation/`）です。

### API（JSON。`/api/admin/**` のため管理者のみ。一般は 403、未ログインは 401。POST・PUT・DELETE は CSRF ヘッダー必須）

| メソッド・パス | 内容 | 成功 |
| --- | --- | --- |
| `GET /api/admin/products` | 一覧（商品コード順） | 200 `[ {商品}, ... ]`（0件は `[]`） |
| `POST /api/admin/products` | 登録 | 201 `{商品}` |
| `PUT /api/admin/products/{code}` | 更新（商品コード以外） | 200 `{商品}` |
| `DELETE /api/admin/products/{code}` | 削除 | 204 |

商品（応答）: `{ "productCode": "00000001", "productName": "みかん", "category": "FOOD", "categoryLabel": "食品", "unitPrice": 120, "caseQuantity": 24 }`（`category` は定数名、`categoryLabel` は表示名）。
要求（POST／PUT）: `{ "productCode": "00000001", "productName": "みかん", "category": "FOOD", "unitPrice": "120", "caseQuantity": "24" }`。`unitPrice`・`caseQuantity` は文字列でも JSON の数値でも可。PUT では `productCode` を無視し、パスの商品コードを対象にします。

エラー:

| 状況 | ステータス | 本文 |
| --- | --- | --- |
| 入力検証エラー（複数項目をまとめて返す。本文なしは全項目 `REQUIRED`） | 400 | `{ "errors": [ { "field": "productName", "type": "REQUIRED" }, ... ] }`（`field` は `productCode` / `productName` / `category` / `unitPrice` / `caseQuantity`。カテゴリが定数名でなければ `category` の `INVALID_FORMAT`、空なら `REQUIRED`） |
| 商品コードが既に登録済み（POST）／在庫がある商品の削除（DELETE） | 409 | `{ "error": "DUPLICATE" }` ／ `{ "error": "HAS_STOCK" }` |
| 更新・削除の対象がない（他の人に先に削除された など） | 404 | `{ "error": "NOT_FOUND" }` |

登録・更新・削除はそれぞれ 1 トランザクション（Spring Data JPA。途中で失敗すれば全体を元に戻します）です。

### 画面の使い方

- 上に入力欄（商品コード・商品名・カテゴリ・単価・ケース入数）とボタン（登録・更新・削除・クリア）、下に一覧、一覧の右上に件数です。商品がなければ「該当する商品がありません。」を出します。
- 一覧の行を選ぶと入力欄に表示されます。選択中は商品コード欄を編集できず、登録は押せません（新しく登録するときは「クリア」）。選択していないと更新・削除は押せません。
- 削除は「選択行があり、入力内容が選択行と完全に一致している」ときだけ押せます（入力を変えると無効になり、元に戻すと有効になります）。
- 登録・更新・削除の前に確認ダイアログを出し、「はい」で実行します。完了は緑のメッセージ（7.5秒）で、一覧を読み直して入力を空にします。
- 入力チェックは Enter キー（入力欄で。選択中は更新、なければ登録）とボタン押下のときだけで、フォーカスが外れても検証しません。エラーはエラーダイアログにまとめて表示します。
- 更新・削除の対象が先に削除されていた（404）ときは「選択された商品は存在しません。」を出して一覧を読み直します。409 は「その商品コードは既に登録されています。別の商品コードを入力してください。」「在庫のある商品は削除できません。」です。文言は `messages.ts`（`productMessages`）にあります。

### 判断事項

- 商品コードは更新で変更できません（変えたいときは削除して登録し直します）。
- 在庫（`stock`）がある商品は削除できません（409 `HAS_STOCK`）。実績（`work_result`）が残っているだけなら削除できます（実績は商品コードを文字列で持ち、外部キーがないため。履歴はそのまま残ります）。
- 在庫の確認と削除は同じトランザクションで行い、確認後に入庫されて外部キー違反になった場合も 409 `HAS_STOCK` にします。同時登録で一意制約に当たった場合も 409 `DUPLICATE` です。
- 登録・更新・削除のボタンの有効・無効は上記のとおり（登録は未選択のときだけ、更新・削除は選択中だけ）です。

### 共通部品（ロケーション・ユーザーなど後続のマスタ画面で使い回せるもの）

バックエンド:

- `common.code.Labeled`（`label()`）と、各区分値 `Role` / `ShelfStatus` / `ResultType` / `Category` の `fromLabel("日本語表示名")`。
- JPA コンバーター `RoleConverter` / `ShelfStatusConverter` / `ResultTypeConverter` / `CategoryConverter`（DB の日本語表示名 ⇔ enum。null は null、不正な値は `IllegalArgumentException`）。エンティティの項目に `@Convert(converter = CategoryConverter.class)` を付けて使う。新しい区分値は `LabelConverter<E>` を継承して同じ形で作れる。
- `common.validation.ValidationFailedException(ValidationResult)` を投げると、`common.web.ValidationExceptionHandler` が 400 `{errors:[{field,type}]}` に変換する。
- 参考実装は `jp.co.apt.mony.product`（`Product` エンティティ＝`Persistable`、`ProductRepository`、`ProductService`、`ProductController`、`ProductExceptionHandler`）。

フロントエンド:

```tsx
import MasterLayout from './components/MasterLayout'

<MasterLayout title="…（S08）" count={rows.length} form={<入力欄 />} buttons={<ボタン列 />}>
  {rows.length === 0 ? <NotFound name="…" /> : <table>…</table>}
</MasterLayout>
```

`MasterLayout` は 見出し → 入力欄 → ボタン列 → 件数（右上）→ 一覧 の配置だけを持ちます。`App.tsx` の `IMPLEMENTED_PAGES`（パス → 画面）に 1 行足すと、その画面が仮ページの代わりに出ます。API の呼び出しは `api/products.ts` が例です。

### テスト

DB なしで、`ProductControllerTest`（WebMvcTest：一覧・登録・重複409・更新・404・在庫あり409・削除・検証400・一般403・未ログイン401）、`ProductServiceTest`、`LabelConverterTest` が動きます。環境変数 `SPRING_DATASOURCE_PASSWORD` があるときだけ `ProductIntegrationTest`（実DB。登録〜削除、在庫あり削除409、実績履歴だけ残る商品の削除、権限、途中失敗でのロールバック）が実行され、各テストの前後でテスト用データを削除します。画面は `pages/ProductAdmin.test.tsx`（Vitest、fetch をモック）です。


## ユーザー管理（FR-09・S10）

管理者だけが使えるユーザーの画面とAPIです。ユーザーID（半角英数4文字・重複不可）、氏名（1〜15文字）、パスワード（8〜14文字。画面では伏せ字）、権限（一般／管理者）を扱います。入力ルールは画面側とサーバー側で同じ（`InputRule` の `userId`・`userName`・`password` ／ `validation/`）です。パスワードは BCrypt（`PasswordEncoder`）でハッシュ化して `app_user.password_hash` に保存し、平文は保存・ログ出力しません。

### API（JSON。`/api/admin/**` のため管理者のみ。一般は 403、未ログインは 401。POST・PUT・DELETE は CSRF ヘッダー必須）

| メソッド・パス | 内容 | 成功 |
| --- | --- | --- |
| `GET /api/admin/users` | 一覧（ユーザーID順） | 200 `[ {ユーザー}, ... ]` |
| `POST /api/admin/users` | 登録 | 201 `{ユーザー}` |
| `PUT /api/admin/users/{userId}` | 更新（ユーザーID以外） | 200 `{ユーザー}` |
| `DELETE /api/admin/users/{userId}` | 削除 | 204 |

ユーザー（応答）: `{ "userId": "U001", "userName": "山田太郎", "role": "GENERAL", "roleLabel": "一般" }`（`role` は定数名 `ADMIN` / `GENERAL`、`roleLabel` は表示名）。パスワードとハッシュは一覧・応答のどこにも含めません。
要求（POST／PUT）: `{ "userId": "U001", "userName": "山田太郎", "password": "（8〜14文字）", "role": "GENERAL" }`。PUT では `userId` を無視し、パスのユーザーIDを対象にします。PUT で `password` が無い・空なら**パスワードは変更しません**（入っていれば 8〜14 文字を検証してハッシュを更新）。POST では `password` は必須です。

エラー:

| 状況 | ステータス | 本文 |
| --- | --- | --- |
| 入力検証エラー（複数項目をまとめて返す。本文なしは全項目 `REQUIRED`） | 400 | `{ "errors": [ { "field": "userId", "type": "INVALID_FORMAT" }, ... ] }`（`field` は `userId` / `userName` / `password` / `role`。権限が定数名でなければ `role` の `INVALID_FORMAT`、空なら `REQUIRED`） |
| ユーザーIDが既に登録済み（POST） | 409 | `{ "error": "DUPLICATE" }` |
| ログイン中の本人の削除（DELETE） | 409 | `{ "error": "SELF" }` |
| 最後の管理者の削除（DELETE）／一般への変更（PUT） | 409 | `{ "error": "LAST_ADMIN" }` |
| 実績（`work_result`）の履歴があるユーザーの削除（DELETE） | 409 | `{ "error": "HAS_RESULTS" }` |
| 更新・削除の対象がない（他の人に先に削除された など） | 404 | `{ "error": "NOT_FOUND" }` |

登録・更新・削除はそれぞれ 1 トランザクション（Spring Data JPA。途中で失敗すれば全体を元に戻します）です。ログイン中のユーザーは認証情報（`@AuthenticationPrincipal` の `auth.AppUser`）から得ます。

### 画面の使い方

- 上に入力欄（ユーザーID・氏名・パスワード・権限）とボタン（登録・更新・削除・クリア）、下に一覧（ユーザーID・氏名・権限。パスワードは出しません）、一覧の右上に件数です。ユーザーがなければ「該当するユーザーがありません。」を出します。
- 一覧の行を選ぶと入力欄に表示されます（パスワード欄は常に空）。選択中はユーザーID欄を編集できず、登録は押せません（新しく登録するときは「クリア」）。選択していないと更新・削除は押せません。更新のときはパスワード欄が空なら変更しません。
- 削除は「選択行があり、入力内容（パスワード欄が空であることを含む）が選択行と完全に一致している」ときだけ押せます。
- 登録・更新・削除の前に確認ダイアログを出し、「はい」で実行します。完了は緑のメッセージ（7.5秒）で、一覧を読み直して入力を空にします。
- パスワードは送信後（成功・失敗にかかわらず）に必ず欄を空にし、画面の状態以外（保存領域など）には残しません。
- 入力チェックは Enter キー（入力欄で。選択中は更新、なければ登録。通信中・確認ダイアログ表示中の Enter は無視）とボタン押下のときだけで、フォーカスが外れても検証しません。エラーはエラーダイアログにまとめて表示します。
- 404 は「選択されたユーザーは存在しません。」を出して一覧を読み直します。409 は「そのユーザーIDは既に登録されています。別のユーザーIDを入力してください。」「ログイン中のユーザーは削除できません。」「最後の管理者は削除できません。また、一般に変更することもできません。」「実績のあるユーザーは削除できません。」です。文言は `messages.ts`（`userMessages`）にあります。

### 判断事項

- ユーザーIDは更新で変更できません（変えたいときは削除して登録し直します）。
- 実績の履歴があるユーザーは削除できません（409 `HAS_RESULTS`。`work_result.user_id` が `app_user` を外部キーで参照するため）。確認後に実績ができて外部キー違反になった場合も 409 です。
- ログイン中の本人は削除できません（409 `SELF`）。最後の管理者は削除も一般への変更もできません（409 `LAST_ADMIN`）。管理者の人数は管理者の行をロックして数えるため、同時操作で管理者が 0 人になることはありません。
- 更新・削除の直前に他の人が同じユーザーを削除した場合、Hibernate の楽観ロック系の例外（`OptimisticLockingFailureException`）は 404 `NOT_FOUND` に変換します。
- 権限は DB に日本語表示名（管理者／一般）で保存します（`RoleConverter`）。

### 注意（セッション）

ユーザーを削除・一般へ変更しても、**そのユーザーが既にログイン中のセッションは残ります**（セッションは DB の Spring Session に保存され、ログイン時の権限のまま有効）。管理者を降格・削除した直後でも、そのユーザーはログアウトするかセッションが切れるまで管理者画面を使えます。セッションの強制破棄は要件外のため実装していません（必要なら `spring_session` の該当 `principal_name` の行を削除すると、次のリクエストから未ログインになります）。

### テスト

DB なしで、`UserControllerTest`（WebMvcTest：一覧でパスワード非返却・登録（ハッシュで保存）・重複409・更新・404・降格 LAST_ADMIN・同時削除404・削除・本人 SELF・最後の管理者・実績あり HAS_RESULTS・検証400・一般403・未ログイン401）、`UserServiceTest`（パスワード変更あり／なし、楽観ロック系例外の404変換、トランザクション境界など）が動きます。環境変数 `SPRING_DATASOURCE_PASSWORD` があるときだけ `UserIntegrationTest`（実DB。登録〜削除、ハッシュ保存、新パスワードでのログイン、実績ありの削除409、権限、途中失敗でのロールバック）が実行され、各テスト（ユーザーID `T00N`）の前後でテスト用データを削除します。画面は `pages/UserAdmin.test.tsx`（Vitest、fetch をモック）です。
