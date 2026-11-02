# Expense Flow — 経費精算・承認システム

React + Spring Boot の日本語業務アプリです。複数明細の下書き、提出、上長承認、経理承認、差戻し・修正・再提出、履歴表示を実装しています。
仕様の正本はルートの [PRODUCT_SPEC.md](../../docs/PRODUCT_SPEC.md) と [TECHNICAL_DESIGN.md](../../docs/TECHNICAL_DESIGN.md) です。

## 必要な環境

- JDK **21**（`java -version` で確認。`JAVA_HOME` も21に設定）
- Node.js **24**、npm
- Docker と Docker Compose v2
- ポート 5173 / 8080 / 8180 / 5432 が空いていること

以降はこの README のある `projects/expense-approval-system` を作業ディレクトリとします。

## 起動

```sh
docker compose up -d
```

Keycloak の初回起動には少し時間がかかります。次の URL が JSON を返すまで待ってください。

```sh
curl --fail http://localhost:8180/realms/expense-training/.well-known/openid-configuration
```

ターミナル1：

```sh
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

ターミナル2：

```sh
cd frontend
npm ci
npm run dev
```

[http://localhost:5173](http://localhost:5173) を開いてログインします。`127.0.0.1` ではなく **localhost** を使用してください。Vite が API と認証リクエストを Spring Boot に中継します。ブラウザはトークンを保存せず、HttpOnly セッション Cookie で通信します。

`local` プロファイルだけが架空の従業員とサンプル申請を挿入します。Keycloak とアプリの `iss` / `sub` は固定 ID で対応しています。サンプルには5つの状態と 49,999 / 50,000 / 50,001 円の境界を含み、履歴の `SEED` は架空の初期状態を表します。

## テストアカウント

すべてのパスワードは **`local-password`**（ローカル専用）です。

| ユーザー | 名前 | 業務上の権限 |
|---|---|---|
| employee-a | 田中 太郎 | 自分の申請。直属上長は manager-a |
| manager-a | 佐藤 健一 | employee-a の申請を承認 |
| employee-b | 伊藤 花 | 自分の申請。直属上長は manager-b |
| manager-b | 鈴木 美咲 | employee-b の申請を承認 |
| accounting-a | 高橋 結衣 | 5万円超で提出された申請を参照、経理承認待ちを処理 |

申請者と承認者を別ブラウザまたはプライベートウィンドウで開くと比較しやすくなります。ログアウトはアプリのセッションを破棄します。Keycloak の SSO セッションは別管理なので、アカウントを切り替える場合は別のブラウザコンテキストを使ってください。

## 操作例

1. employee-a でログインし「新しい申請」から目的と明細を入力、下書きを保存。
2. 詳細画面で上長へ提出。
3. manager-a で同じ申請を開いて承認。50,000円以下は完了、50,001円以上は経理へ。
4. accounting-a で承認すると最終承認済みになる。
5. 差戻しには理由が必須。employee-a が修正・再提出すると上長承認から再開する。

一覧の期間条件は **作成日（日本時間）**、申請者条件は **完全一致の利用者ID** です。1ページ20件、APIの上限は100件です。上長には部下の下書きも参照可能ですが、承認操作は提出後だけに制限しています。

## ビルド・テスト

Docker 不要のテスト（OIDC を模擬、DBはH2）：

```sh
cd backend
./gradlew test bootJar
```

実際の PostgreSQL を使う統合テスト（業務用DBとは必ず分離）：

```sh
docker compose exec -T postgres createdb -U expense expenses_test
cd backend
TEST_DB_URL=jdbc:postgresql://localhost:5432/expenses_test \
TEST_DB_USER=expense TEST_DB_PASSWORD=expense-local ./gradlew test --rerun-tasks
```

`createdb` は初回だけ実行します。テストは対象DBの申請・履歴を削除するため、業務用DBを指定しないでください。金額境界、再提出、権限外アクセス、CSRF、未登録ユーザー、競合、トランザクション失敗を検証します。同時実行テストは2つの独立トランザクションを同期させて実行します。

```sh
cd frontend
npm ci
npm run build
npm test
```

実ログインを含む E2E（Compose とバックエンドを起動した状態）：

```sh
cd frontend
npx playwright install chromium
npm run test:e2e
```

Playwright が Vite を起動します。既に localhost:5173 で実行中なら再利用します。独立したブラウザセッションで、50,001円の上長→経理承認、および50,000円の差戻し→修正→再提出→上長承認を確認します。生成した架空の申請はローカルDBに残ります。

GitHub Actions は PostgreSQL 統合テスト、Java ビルド、TypeScript チェック、React ビルド、フロントテスト、Compose + Keycloak + Chromium の E2E を実行します。

## 設定

| 環境変数 | 既定値 / 用途 |
|---|---|
| DB_URL | jdbc:postgresql://localhost:5432/expenses |
| DB_USER / DB_PASSWORD | expense / expense-local |
| OIDC_ISSUER | http://localhost:8180/realms/expense-training |
| OIDC_CLIENT_SECRET | expense-local-secret（Compose側にも同じ値） |
| APP_ORIGIN | http://localhost:5173 |
| COOKIE_SECURE | false（HTTPSを使う構成ではtrue） |
| SQL_STATISTICS | false（trueでHibernateの統計を出力） |

SQL 調査には `--logging.level.org.hibernate.SQL=DEBUG --logging.level.org.hibernate.orm.jdbc.bind=TRACE` を起動引数に指定できます。入力内容もログに出るためローカル調査用です。金額はサーバーで整数円を合算し、1明細1〜10億円、最大100明細、合計はlongで扱います。バージョンが古い更新は409を返します。

ポートやIssuerを変える場合、KeycloakのredirectUris、DB従業員のissuer、アプリのAPP_ORIGIN/OIDC_ISSUERを併せて変更してください。realmのsecretはKeycloakの環境変数置換で注入します。既存realmの設定を変えるにはKeycloak側で更新するかローカルコンテナを作り直します。

## 停止・リセット

アプリとViteは各ターミナルで Ctrl+C。インフラだけ停止：

```sh
docker compose stop
```

ローカルの架空データをすべて破棄し、初期状態に戻す場合のみ実行：

```sh
docker compose down -v
docker compose up -d
```

Keycloak はローカル開発モードで、コンテナ再作成時にrealmを再インポートします。PostgreSQL はnamed volumeに永続化されます。本番配備は初期仕様の対象外です。

## API

すべての業務APIは認証・従業員登録が必要です。更新系は `/api/csrf` で取得したトークンを返却された `headerName` のHTTPヘッダーに付与します。ログイン後・ログアウト後にもトークンを取り直します。APIは未認証時に401を返します。

| メソッド | パス | 用途 |
|---|---|---|
| GET | /api/csrf | セッションのCSRFトークン |
| GET | /api/me | 現在の業務ユーザー |
| GET | /api/expenses | scope=mine/approvals/all、status、applicant、from/to、page/size |
| POST | /api/expenses | purpose と lines から下書き作成 |
| GET | /api/expenses/{id} | 明細・履歴・操作可否 |
| PUT | /api/expenses/{id} | version、purpose、lines で編集 |
| POST | /api/expenses/{id}/submit | version を指定して提出 |
| POST | /api/expenses/{id}/approve | version を指定して承認 |
| POST | /api/expenses/{id}/return | version、comment で差戻し |
| POST | /logout | CSRF保護されたアプリのログアウト |

閲覧権限のない申請IDには404、閲覧可能でも操作権限がなければ403、状態・更新の競合は409、入力不備は400です。詳細の操作可否はUIの補助であり、各変更APIが権限を再判定します。

重要な設計判断は [DECISIONS.md](docs/DECISIONS.md) を参照してください。
