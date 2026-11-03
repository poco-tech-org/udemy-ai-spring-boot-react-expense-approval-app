# ローカル検証記録

2026-11-02（日本時間）に実行。

| 検証 | 結果 |
|---|---|
| Docker Compose: PostgreSQL 17.6 / Keycloak 26.4.2 | 起動・realm自動インポート成功 |
| Spring Boot 4.1.2 / Java 21 | 起動・bootJarビルド成功 |
| バックエンド統合テスト（H2） | 13件成功 |
| バックエンド統合テスト（PostgreSQL） | 13件成功 |
| TypeScript + Vite ビルド | 成功 |
| Vitest / React Testing Library | 5件成功 |
| Playwright + 実Keycloak + PostgreSQL | 2件成功 |
| npm audit | 指摘0件 |
| デスクトップ1440px / モバイル390px | ログイン後画面を撮影して確認 |

バックエンドは金額境界、複数明細合算、差戻し・再提出、自己承認禁止、担当外参照・編集禁止、未提出・最終承認済みの操作禁止、有効な上長の要求、ページング、古いversion、同時承認、ロールバック、未登録ユーザー、401、CSRF、小数金額拒否、明細のみ編集時のversion更新を確認。

フロントはCSRFヘッダー、セッション失効、競合メッセージ、整数円表示、保存中の二重操作抑制とエラー時の入力保持を確認。

E2Eは独立ブラウザコンテキストで以下を確認。

1. 50,001円の新規作成・提出 → 上長承認 → 経理承認 → 申請者による最終状態と履歴の確認。
2. 50,000円の新規作成・提出 → 上長差戻し → 申請者による修正・再提出 → 上長のみで最終承認。

GitHub Actionsの定義は追加済み。リモートのActions実行はこの作業では行っていない。再現コマンドはアプリREADMEを参照。

リポジトリルートへの構成変更後も、Compose設定の検証、両アプリのビルド、バックエンド13件・フロント5件・E2E2件の再実行に成功。既存のPostgreSQLボリュームを引き継ぎ、KeycloakのマウントとViteの起動元を新パスへ更新した。

## 2026-11-03 CI失敗の調査・修正

[GitHub Actions run 41461443241](https://github.com/poco-tech-org/udemy-ai-spring-boot-react-expense-approval-app/actions/runs/41461443241) は backend が失敗し、frontend と e2e は成功。backend のテスト起動時に、アプリ設定から引き継いだ issuer-uri に対する OIDC discovery が実行され、Keycloak 未起動のため13件すべてが失敗していた。以前のローカル検証では Keycloak が動いていたため、この依存を検出できていなかった。

同じ失敗を、到達不能な `OIDC_ISSUER=http://127.0.0.1:1/realms/unavailable` を指定して再現。テスト専用の ClientRegistrationRepository をネットワーク探索なしで構築するよう修正し、同条件で H2 と PostgreSQL の各13件、および bootJar が成功することを確認した。

CI の backend ジョブにも到達不能な issuer を指定し、OIDC discovery への依存が再混入すれば失敗する構成にした。失敗時はバックエンドのテストレポートをartifactとして保存する。修正後のリモートCI結果はpush後に別途確認する。
