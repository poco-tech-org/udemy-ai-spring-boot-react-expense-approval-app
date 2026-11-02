import { test, expect, type Page, type Browser } from "@playwright/test";
async function login(page: Page, user: string) {
  await page.goto("/");
  await page.getByRole("link", { name: /組織アカウントでログイン/ }).click();
  await page.locator("#username").fill(user);
  await page.locator("#password").fill("local-password");
  await page.locator("#kc-login").click();
  await expect(
    page.getByRole("heading", { name: "自分の経費申請" }),
  ).toBeVisible();
}
async function create(page: Page, amount: number) {
  const title = "E2E 出張 " + Date.now();
  await page.getByRole("link", { name: "＋ 新しい申請" }).click();
  await page.getByLabel("申請の目的").fill(title);
  await page.getByLabel("利用先").fill("サンプル鉄道");
  await page.getByLabel("利用目的").fill("取引先訪問");
  await page.getByLabel("金額（円）").fill(String(amount));
  await page.getByRole("button", { name: "下書きを保存" }).click();
  await expect(page.getByRole("heading", { name: title })).toBeVisible();
  await page.getByRole("button", { name: "上長に提出する" }).click();
  await expect(page.getByRole("status")).toContainText("申請を提出しました");
  return page.url();
}
test("50001円の申請を上長と経理が承認し、履歴を確認できる", async ({
  browser,
}) => {
  const employee = await browser.newContext(),
    manager = await browser.newContext(),
    accounting = await browser.newContext();
  try {
    const ep = await employee.newPage();
    await login(ep, "employee-a");
    const url = await create(ep, 50001);
    const mp = await manager.newPage();
    await login(mp, "manager-a");
    await mp.goto(url);
    await mp.getByRole("button", { name: "承認する", exact: true }).click();
    await expect(mp.getByText("経理承認待ち", { exact: true })).toBeVisible();
    const ap = await accounting.newPage();
    await login(ap, "accounting-a");
    await ap.goto(url);
    await ap.getByRole("button", { name: "承認する", exact: true }).click();
    await expect(ap.getByText("承認済み", { exact: true })).toBeVisible();
    await ep.reload();
    await expect(ep.getByText("承認済み", { exact: true })).toBeVisible();
    await expect(
      ep.getByRole("heading", { name: "申請・承認履歴" }),
    ).toBeVisible();
  } finally {
    await employee.close();
    await manager.close();
    await accounting.close();
  }
});
test("50000円の差戻し・修正・再提出は上長承認で完了する", async ({
  browser,
}) => {
  const employee = await browser.newContext(),
    manager = await browser.newContext();
  try {
    const ep = await employee.newPage();
    await login(ep, "employee-a");
    const url = await create(ep, 50000);
    const mp = await manager.newPage();
    await login(mp, "manager-a");
    await mp.goto(url);
    await mp.getByRole("button", { name: "差戻し", exact: true }).click();
    await mp
      .getByLabel("差戻し理由（必須）")
      .fill("目的を具体的にしてください");
    await mp.getByRole("button", { name: "理由を添えて差し戻す" }).click();
    await expect(mp.getByRole("status")).toContainText("差し戻しました");
    await ep.reload();
    await ep.getByRole("link", { name: "内容を編集" }).click();
    await ep.getByLabel("利用目的").fill("新規顧客への営業訪問");
    await ep.getByRole("button", { name: "下書きを保存" }).click();
    await ep.getByRole("button", { name: "再提出する" }).click();
    await expect(ep.getByRole("status")).toContainText("提出しました");
    await mp.reload();
    await mp.getByRole("button", { name: "承認する", exact: true }).click();
    await expect(mp.getByText("承認済み", { exact: true })).toBeVisible();
  } finally {
    await employee.close();
    await manager.close();
  }
});
