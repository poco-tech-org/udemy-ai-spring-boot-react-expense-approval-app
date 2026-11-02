import { useState, useEffect } from "react";
import { Routes, Route, NavLink, Link } from "react-router-dom";
import { api, ApiError, type User } from "./api";
import { ErrorMessage } from "./ui";
import ExpenseList from "./pages/ExpenseList";
import ExpenseEditor from "./pages/ExpenseEditor";
import ExpenseDetail from "./pages/ExpenseDetail";
import "./style.css";
export default function App() {
  const [user, setUser] = useState<User | null>(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState<unknown>(null);
  useEffect(() => {
    api<User>("/api/me")
      .then(setUser)
      .catch((e) => {
        if (!(e instanceof ApiError && e.status === 401)) setError(e);
      })
      .finally(() => setLoading(false));
  }, []);
  async function logout() {
    try {
      await api("/logout", { method: "POST" });
      window.location.assign("/");
    } catch (e) {
      setError(e);
    }
  }
  if (loading)
    return (
      <main className="welcome">
        <p role="status">読み込んでいます…</p>
      </main>
    );
  if (!user)
    return (
      <main className="welcome">
        <div className="brand">
          EF<span>Expense Flow</span>
        </div>
        <p className="eyebrow">経費精算・承認システム</p>
        <h1>
          経費の申請から承認まで、
          <br />
          ひとつの流れに。
        </h1>
        <p>
          申請の状況も、承認の履歴も。
          <br />
          チームの経費をわかりやすく管理します。
        </p>
        <ErrorMessage error={error} />
        <a className="button primary" href="/oauth2/authorization/keycloak">
          組織アカウントでログイン →
        </a>
        <small>認証には組織の Keycloak アカウントを使用します。</small>
      </main>
    );
  return (
    <>
      <div className="shell">
        <aside>
          <Link className="brand" to="/">
            EF
            <span>
              Expense Flow<small>経費精算・承認</small>
            </span>
          </Link>
          <p className="nav-label">WORKSPACE</p>
          <nav>
            <NavLink to="/" end>
              ▤　自分の申請
            </NavLink>
            {(user.manager || user.accounting) && (
              <>
                <NavLink to="/approvals">✓　承認待ち</NavLink>
                <NavLink to="/records">◷　参照できる申請</NavLink>
              </>
            )}
          </nav>
          <div className="sidebar-note">
            承認ルール
            <small>
              50,000円以下は上長承認。
              <br />
              50,000円超は経理の追加承認が必要です。
            </small>
          </div>
          <div className="profile">
            <div className="avatar">{user.name[0]}</div>
            <div>
              {user.name}
              <small>
                {user.accounting
                  ? "経理担当者"
                  : user.manager
                    ? "上長"
                    : "従業員"}
              </small>
            </div>
            <button className="link-button" onClick={logout}>
              ログアウト
            </button>
          </div>
        </aside>
        <main className="workspace">
          <div className="topbar">
            経費管理 <span>社内ワークスペース</span>
          </div>
          <ErrorMessage error={error} />
          <Routes>
            <Route path="/" element={<ExpenseList scope="mine" />} />
            <Route
              path="/approvals"
              element={<ExpenseList scope="approvals" />}
            />
            <Route path="/records" element={<ExpenseList scope="all" />} />
            <Route path="/expenses/new" element={<ExpenseEditor />} />
            <Route path="/expenses/:id/edit" element={<ExpenseEditor />} />
            <Route path="/expenses/:id" element={<ExpenseDetail />} />
            <Route
              path="*"
              element={
                <p>
                  ページが見つかりません。<Link to="/">申請一覧へ戻る</Link>
                </p>
              }
            />
          </Routes>
        </main>
      </div>
    </>
  );
}
