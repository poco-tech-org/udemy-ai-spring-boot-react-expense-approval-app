import { ApiError, states, type Status } from "./api";
export const date = (s: string) => new Date(s).toLocaleString("ja-JP");
export function ErrorMessage({ error }: { error: unknown }) {
  return error ? (
    <div className="alert error" role="alert">
      {error instanceof Error ? error.message : String(error)}
      {error instanceof ApiError && error.status === 401 && (
        <a href="/oauth2/authorization/keycloak">再ログイン</a>
      )}
    </div>
  ) : null;
}
export function Badge({ status }: { status: Status }) {
  return <span className={"badge " + status}>{states[status]}</span>;
}
