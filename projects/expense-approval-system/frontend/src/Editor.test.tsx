import { afterEach, it, expect, vi } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Editor from "./pages/ExpenseEditor";
afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});
it("保存中は二重操作を抑え、失敗時も入力を保持する", async () => {
  let finish!: (response: Response) => void;
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(
      new Response(
        JSON.stringify({ headerName: "X-CSRF-TOKEN", token: "token" }),
        { headers: { "Content-Type": "application/json" } },
      ),
    )
    .mockImplementationOnce(
      () =>
        new Promise<Response>((resolve) => {
          finish = resolve;
        }),
    );
  vi.stubGlobal("fetch", fetch);
  render(
    <MemoryRouter>
      <Editor />
    </MemoryRouter>,
  );
  fireEvent.change(screen.getByLabelText("申請の目的"), {
    target: { value: "営業訪問" },
  });
  fireEvent.change(screen.getByLabelText("利用先"), {
    target: { value: "鉄道会社" },
  });
  fireEvent.change(screen.getByLabelText("利用目的"), {
    target: { value: "取引先へ移動" },
  });
  fireEvent.change(screen.getByLabelText("金額（円）"), {
    target: { value: "50001" },
  });
  expect(screen.getByText("￥50,001")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "下書きを保存" }));
  await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  expect(
    screen.getByRole("button", { name: "保存しています…" }),
  ).toBeDisabled();
  finish(
    new Response(JSON.stringify({ detail: "一時的に保存できません" }), {
      status: 409,
      headers: { "Content-Type": "application/json" },
    }),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "一時的に保存できません",
  );
  expect(screen.getByLabelText("申請の目的")).toHaveValue("営業訪問");
  expect(screen.getByRole("button", { name: "下書きを保存" })).toBeEnabled();
});
