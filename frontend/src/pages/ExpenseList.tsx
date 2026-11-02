import { useState, useEffect, useCallback } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, yen, states, type Page } from "../api";
import { ErrorMessage, Badge } from "../ui";
export default function ExpenseList({ scope }: { scope: string }) {
  const [params, setParams] = useSearchParams(),
    [data, setData] = useState<Page | null>(null),
    [error, setError] = useState<unknown>(null),
    [loading, setLoading] = useState(false),
    [revision, setRevision] = useState(0);
  const key = params.toString();
  const page = Number(params.get("page") || 0);
  const reload = useCallback(() => {
    let active = true;
    setLoading(true);
    setError(null);
    const q = new URLSearchParams(key);
    q.set("scope", scope);
    api<Page>("/api/expenses?" + q)
      .then((d) => {
        if (active) setData(d);
      })
      .catch((e) => {
        if (active) setError(e);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [key, scope, revision]);
  useEffect(reload, [reload]);
  const filter = (name: string, value: string) => {
    const q = new URLSearchParams(params);
    value ? q.set(name, value) : q.delete(name);
    q.delete("page");
    setParams(q);
  };
  return (
    <>
      <header className="page-header">
        <div>
          <p className="eyebrow">EXPENSE MANAGEMENT</p>
          <h1>
            {scope === "mine"
              ? "自分の経費申請"
              : scope === "approvals"
                ? "承認待ちの申請"
                : "参照できる申請"}
          </h1>
          <p>
            {scope === "mine"
              ? "日々の経費をまとめて申請。進み具合をここで確認できます。"
              : "申請内容を確認して、承認・差戻しを行ってください。"}
          </p>
        </div>
        <Link className="button primary" to="/expenses/new">
          ＋ 新しい申請
        </Link>
      </header>
      <div className="info-banner">
        <span>ⓘ</span>
        <div>
          金額に応じて、承認経路を自動で設定
          <small>
            合計50,000円を超える申請は、上長の承認後に経理へ送られます。
          </small>
        </div>
      </div>
      <section className="panel">
        <div className="panel-title">
          <h2>{scope === "approvals" ? "対応が必要な申請" : "申請一覧"}</h2>
          <span>{data?.totalElements ?? "—"} 件</span>
          <button
            className="link-button"
            onClick={() => setRevision((n) => n + 1)}
          >
            更新
          </button>
        </div>
        <div className="filters">
          <label>
            申請状態
            <select
              value={params.get("status") || ""}
              onChange={(e) => filter("status", e.target.value)}
            >
              <option value="">すべての状態</option>
              {Object.entries(states).map(([s, label]) => (
                <option key={s} value={s}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label>
            作成日（開始）
            <input
              type="date"
              value={params.get("from") || ""}
              onChange={(e) => filter("from", e.target.value)}
            />
          </label>
          <label>
            作成日（終了）
            <input
              type="date"
              value={params.get("to") || ""}
              onChange={(e) => filter("to", e.target.value)}
            />
          </label>
          {scope !== "mine" && (
            <label>
              申請者ID
              <input
                placeholder="例：employee-a"
                value={params.get("applicant") || ""}
                onChange={(e) => filter("applicant", e.target.value)}
              />
            </label>
          )}
        </div>
        <ErrorMessage error={error} />
        {loading ? (
          <div className="empty" role="status">
            申請を読み込んでいます…
          </div>
        ) : (
          <>
            <div className="table-wrap">
              <table className="expense-table">
                <thead>
                  <tr>
                    <th>申請の目的</th>
                    <th>申請者</th>
                    <th>作成日</th>
                    <th>状態</th>
                    <th className="numeric">合計金額</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {data?.content.map((e) => (
                    <tr key={e.id}>
                      <td>
                        <Link className="expense-link" to={"/expenses/" + e.id}>
                          {e.purpose || "無題の下書き"}
                        </Link>
                        <small>#{e.id.slice(0, 8)}</small>
                      </td>
                      <td>{e.ownerName}</td>
                      <td>
                        {new Date(e.createdAt).toLocaleDateString("ja-JP")}
                      </td>
                      <td>
                        <Badge status={e.status} />
                      </td>
                      <td className="numeric money">{yen(e.total)}</td>
                      <td>
                        <Link
                          aria-label={e.purpose + "の詳細"}
                          to={"/expenses/" + e.id}
                        >
                          →
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {data?.content.length === 0 && (
              <div className="empty">
                <h3>
                  {scope === "approvals"
                    ? "現在、承認待ちの申請はありません"
                    : "該当する申請はありません"}
                </h3>
                <p>条件を変更するか、新しい申請を作成してください。</p>
              </div>
            )}
          </>
        )}
        <div className="pagination">
          <span>
            {data
              ? `${data.totalElements} 件中 ${data.content.length ? data.number * 20 + 1 : 0}〜${data.number * 20 + data.content.length} 件`
              : ""}
          </span>
          <button
            disabled={page <= 0 || loading}
            onClick={() =>
              setParams((p) => {
                p.set("page", String(page - 1));
                return p;
              })
            }
          >
            前へ
          </button>
          <span>
            {page + 1} / {Math.max(data?.totalPages || 1, 1)}
          </span>
          <button
            disabled={!data || page + 1 >= data.totalPages || loading}
            onClick={() =>
              setParams((p) => {
                p.set("page", String(page + 1));
                return p;
              })
            }
          >
            次へ
          </button>
        </div>
      </section>
      <p className="footer-note">
        申請内容と操作履歴は、関係者だけが確認できます。
      </p>
    </>
  );
}
