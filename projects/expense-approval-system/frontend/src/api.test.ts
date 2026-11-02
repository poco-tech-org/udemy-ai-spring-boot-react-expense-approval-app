import { afterEach, describe, it, expect, vi } from "vitest";
import { api, ApiError, yen } from "./api";
afterEach(() => vi.unstubAllGlobals());
describe("API client", () => {
  it("取得したCSRFトークンを変更リクエストに付与する", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ token: "csrf-value", headerName: "X-CSRF-TOKEN" }),
          { headers: { "Content-Type": "application/json" } },
        ),
      )
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ ok: true }), {
          headers: { "Content-Type": "application/json" },
        }),
      );
    vi.stubGlobal("fetch", fetch);
    expect(await api("/api/expenses", { method: "POST", body: "{}" })).toEqual({
      ok: true,
    });
    expect(
      new Headers(fetch.mock.calls[1][1].headers).get("X-CSRF-TOKEN"),
    ).toBe("csrf-value");
  });
  it("セッション期限切れを通知する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response("{}", { status: 401 })),
    );
    await expect(api("/api/me")).rejects.toMatchObject({ status: 401 });
  });
  it("競合の業務メッセージを保持する", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ detail: "更新されています" }), {
          status: 409,
        }),
      ),
    );
    await expect(api("/api/expenses")).rejects.toThrow("更新されています");
  });
  it("整数円として表示する", () => {
    expect(yen(50001)).toContain("50,001");
  });
});
