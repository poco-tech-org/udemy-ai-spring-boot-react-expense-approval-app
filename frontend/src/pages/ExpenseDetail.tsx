import { useState, useEffect, useCallback } from "react";
import { Link, useParams, useLocation } from "react-router-dom";
import { api, ApiError, yen, states, type Detail } from "../api";
import { ErrorMessage, Badge, date } from "../ui";
export default function ExpenseDetail() {
  const location = useLocation();
  const { id } = useParams();
  const [data, setData] = useState<Detail | null>(null),
    [error, setError] = useState<unknown>(null),
    [busy, setBusy] = useState(false),
    [reason, setReason] = useState(""),
    [returning, setReturning] = useState(false),
    [message, setMessage] = useState(location.state?.message || "");
  const load = useCallback(
    () => api<Detail>("/api/expenses/" + id).then(setData),
    [id],
  );
  useEffect(() => {
    load().catch(setError);
  }, [load]);
  async function act(action: string) {
    if (busy || !data) return;
    setBusy(true);
    setError(null);
    setMessage("");
    try {
      setData(
        await api<Detail>(`/api/expenses/${id}/${action}`, {
          method: "POST",
          body: JSON.stringify({
            version: data.expense.version,
            comment: reason,
          }),
        }),
      );
      setReturning(false);
      setReason("");
      setMessage(
        action === "submit"
          ? "申請を提出しました。"
          : action === "approve"
            ? "承認しました。"
            : "理由を添えて差し戻しました。",
      );
    } catch (e) {
      setError(e);
      if (e instanceof ApiError && e.status === 409)
        await load().catch(() => {});
    } finally {
      setBusy(false);
    }
  }
  if (!data)
    return (
      <>
        <ErrorMessage error={error} />
        {!error && <p role="status">読み込んでいます…</p>}
      </>
    );
  const e = data.expense;
  const actions: Record<string, string> = {
    CREATE: "下書き作成",
    EDIT: "内容を編集",
    SUBMIT: "提出",
    APPROVE: "承認",
    RETURN: "差戻し",
    SEED: "初期サンプル",
  };
  return (
    <>
      <Link className="back" to="/">
        ← 申請一覧
      </Link>
      <header className="page-header">
        <div>
          <p className="eyebrow">EXPENSE #{e.id.slice(0, 8)}</p>
          <h1>{e.purpose || "無題の下書き"}</h1>
          <p>
            {e.ownerName} · {date(e.createdAt)} 作成
          </p>
        </div>
        <Badge status={e.status} />
      </header>
      <ErrorMessage error={error} />
      {message && (
        <div className="alert success" role="status">
          {message}
        </div>
      )}
      <section className="panel form-panel">
        <div className="detail-total">
          <div>
            <small>申請合計</small>
            <strong>{yen(e.total)}</strong>
          </div>
          <p>
            {e.total > 50000 ? "上長承認 → 経理承認" : "上長承認"}
            <small>
              {e.submittedAt
                ? "提出日時：" + date(e.submittedAt)
                : "まだ提出されていません"}
            </small>
          </p>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>利用日</th>
                <th>利用先 / 目的</th>
                <th>区分</th>
                <th className="numeric">金額</th>
              </tr>
            </thead>
            <tbody>
              {data.lines.map((l, i) => (
                <tr key={i}>
                  <td>{l.usedOn}</td>
                  <td>
                    {l.merchant}
                    <small>{l.description}</small>
                  </td>
                  <td>{l.category}</td>
                  <td className="numeric">{yen(l.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data.lines.length === 0 && (
          <p className="empty">
            明細はありません。編集画面から追加してください。
          </p>
        )}
        <div className="actions">
          {data.editable && (
            <Link className="button" to={"/expenses/" + id + "/edit"}>
              内容を編集
            </Link>
          )}
          {data.submittable && (
            <button
              className="primary"
              disabled={busy}
              onClick={() => act("submit")}
            >
              {e.status === "RETURNED" ? "再提出する" : "上長に提出する"}
            </button>
          )}
          {data.approvable && (
            <>
              <button disabled={busy} onClick={() => setReturning(!returning)}>
                差戻し
              </button>
              <button
                className="primary"
                disabled={busy}
                onClick={() => act("approve")}
              >
                承認する
              </button>
            </>
          )}
        </div>
        {returning && (
          <form
            className="return-form"
            onSubmit={(ev) => {
              ev.preventDefault();
              act("return");
            }}
          >
            <label>
              差戻し理由（必須）
              <textarea
                required
                maxLength={1000}
                value={reason}
                onChange={(ev) => setReason(ev.target.value)}
                placeholder="申請者が修正できるよう、理由を具体的に入力してください。"
              />
            </label>
            <button disabled={busy || !reason.trim()} type="submit">
              理由を添えて差し戻す
            </button>
          </form>
        )}
      </section>
      <section className="panel form-panel">
        <h2>申請・承認履歴</h2>
        <ol className="timeline">
          {data.history.map((h) => (
            <li key={h.id}>
              <div className="timeline-dot" />
              <div>
                <strong>{actions[h.action] || h.action}</strong>
                <span className="muted">
                  {h.actorName || h.actorId} · {date(h.occurredAt)}
                </span>
                <p>
                  {states[h.fromStatus]} → {states[h.toStatus]}　{yen(h.total)}
                </p>
                {h.comment && <blockquote>{h.comment}</blockquote>}
              </div>
            </li>
          ))}
        </ol>
      </section>
    </>
  );
}
