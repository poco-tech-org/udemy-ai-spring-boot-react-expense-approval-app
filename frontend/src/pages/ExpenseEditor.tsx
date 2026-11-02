import { useState, useEffect } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, yen, type Line, type Detail } from "../api";
import { ErrorMessage } from "../ui";
const emptyLine = (): Line => ({
  usedOn: new Date().toLocaleDateString("sv-SE"),
  merchant: "",
  category: "交通費",
  description: "",
  amount: 0,
});
export default function ExpenseEditor() {
  const { id } = useParams(),
    navigate = useNavigate();
  const [purpose, setPurpose] = useState(""),
    [lines, setLines] = useState<Line[]>([emptyLine()]),
    [version, setVersion] = useState(0),
    [loading, setLoading] = useState(!!id),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(null);
  useEffect(() => {
    if (id)
      api<Detail>("/api/expenses/" + id)
        .then((d) => {
          if (!d.editable) throw new Error("この申請は編集できません。");
          setPurpose(d.expense.purpose);
          setLines(d.lines);
          setVersion(d.expense.version);
        })
        .catch(setError)
        .finally(() => setLoading(false));
  }, [id]);
  const total = lines.reduce((n, l) => n + (Number(l.amount) || 0), 0);
  function change(i: number, k: keyof Line, v: string | number) {
    setLines((prev) =>
      prev.map((line, n) => (n === i ? { ...line, [k]: v } : line)),
    );
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError(null);
    try {
      const d = await api<Detail>("/api/expenses" + (id ? "/" + id : ""), {
        method: id ? "PUT" : "POST",
        body: JSON.stringify({ version, purpose, lines }),
      });
      navigate("/expenses/" + d.expense.id, {
        state: { message: "下書きを保存しました。" },
      });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  if (loading) return <p role="status">読み込んでいます…</p>;
  return (
    <>
      <Link className="back" to={id ? "/expenses/" + id : "/"}>
        ← 申請に戻る
      </Link>
      <header className="page-header">
        <div>
          <p className="eyebrow">NEW EXPENSE</p>
          <h1>{id ? "申請を編集" : "新しい経費申請"}</h1>
          <p>経費の明細を入力して、まずは下書きに保存しましょう。</p>
        </div>
      </header>
      <ErrorMessage error={error} />
      <form onSubmit={save}>
        <fieldset disabled={busy} className="form-fieldset">
          <section className="panel form-panel">
            <h2>申請の基本情報</h2>
            <label>
              申請の目的
              <input
                maxLength={200}
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                placeholder="例：11月 大阪出張に伴う交通費・宿泊費"
              />
            </label>
            <small>
              目的は下書き保存後も編集できます。提出時には必須です。
            </small>
          </section>
          <section className="panel form-panel">
            <h2>
              経費明細 <span className="muted">{lines.length} 件</span>
            </h2>
            {lines.map((l, i) => (
              <div className="line-card" key={i}>
                <div className="line-title">
                  <strong>明細 {i + 1}</strong>
                  <button
                    type="button"
                    onClick={() => setLines(lines.filter((_, n) => n !== i))}
                  >
                    削除
                  </button>
                </div>
                <div className="line-grid">
                  <label>
                    利用日
                    <input
                      required
                      type="date"
                      value={l.usedOn}
                      onChange={(e) => change(i, "usedOn", e.target.value)}
                    />
                  </label>
                  <label>
                    利用先
                    <input
                      required
                      maxLength={200}
                      value={l.merchant}
                      onChange={(e) => change(i, "merchant", e.target.value)}
                      placeholder="利用した店舗・会社名"
                    />
                  </label>
                  <label>
                    経費区分
                    <select
                      value={l.category}
                      onChange={(e) => change(i, "category", e.target.value)}
                    >
                      {["交通費", "宿泊費", "会議費", "消耗品費", "その他"].map(
                        (c) => (
                          <option key={c}>{c}</option>
                        ),
                      )}
                    </select>
                  </label>
                  <label>
                    金額（円）
                    <input
                      required
                      type="number"
                      min="1"
                      max="1000000000"
                      step="1"
                      value={l.amount || ""}
                      onChange={(e) =>
                        change(i, "amount", Number(e.target.value))
                      }
                    />
                  </label>
                  <label className="wide">
                    利用目的
                    <input
                      required
                      maxLength={500}
                      value={l.description}
                      onChange={(e) => change(i, "description", e.target.value)}
                      placeholder="業務との関連がわかる内容"
                    />
                  </label>
                </div>
              </div>
            ))}
            <button
              type="button"
              className="add-line"
              disabled={lines.length >= 100}
              onClick={() => setLines([...lines, emptyLine()])}
            >
              ＋ 明細を追加
            </button>
          </section>
          <div className="save-bar">
            <div>
              合計金額<strong>{yen(total)}</strong>
              <small>
                {total > 50000 ? "上長承認 → 経理承認" : "上長承認"}
              </small>
            </div>
            <button className="primary" type="submit">
              {busy ? "保存しています…" : "下書きを保存"}
            </button>
          </div>
        </fieldset>
      </form>
    </>
  );
}
