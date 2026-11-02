export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function api<T>(
  url: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.method && options.method !== "GET") {
    const csrf = await fetch("/api/csrf", { credentials: "same-origin" });
    if (!csrf.ok)
      throw new ApiError(
        csrf.status,
        "セキュリティ情報を取得できません。再読み込みしてください。",
      );
    const token = await csrf.json();
    headers.set(token.headerName, token.token);
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(url, {
    ...options,
    headers,
    credentials: "same-origin",
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      response.status === 401
        ? "ログインの有効期限が切れました。再ログインしてください。"
        : body.detail || "通信に失敗しました。再度お試しください。",
    );
  }
  if (
    response.status === 204 ||
    !response.headers.get("content-type")?.includes("json")
  )
    return undefined as T;
  return response.json();
}
export const yen = (amount: number) =>
  new Intl.NumberFormat("ja-JP", { style: "currency", currency: "JPY" }).format(
    amount,
  );
export const states = {
  DRAFT: "下書き",
  SUBMITTED: "上長承認待ち",
  ACCOUNTING_REVIEW: "経理承認待ち",
  RETURNED: "差戻し",
  APPROVED: "承認済み",
};
export type Status = keyof typeof states;
export type User = {
  id: string;
  name: string;
  manager: boolean;
  accounting: boolean;
  managerId: string | null;
};
export type Summary = {
  id: string;
  version: number;
  purpose: string;
  ownerId: string;
  ownerName: string;
  status: Status;
  total: number;
  createdAt: string;
  submittedAt: string | null;
};
export type Line = {
  usedOn: string;
  merchant: string;
  category: string;
  description: string;
  amount: number;
};
export type History = {
  id: string;
  actorId: string;
  actorName: string;
  action: string;
  occurredAt: string;
  fromStatus: Status;
  toStatus: Status;
  total: number;
  comment: string | null;
};
export type Detail = {
  expense: Summary;
  lines: Line[];
  history: History[];
  editable: boolean;
  submittable: boolean;
  approvable: boolean;
};
export type Page = {
  content: Summary[];
  totalElements: number;
  totalPages: number;
  number: number;
};
