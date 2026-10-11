import { expect, test } from "@playwright/test";

import { formAlert, formButton, logIn, signUp, TEST_PASSWORD, uniqueEmail } from "./helpers";

/**
 * 认证相关的端到端行为。
 *
 * 这些是 API 测试覆盖不到的：登录态在浏览器里如何表现、
 * 路由守卫怎么跳转、表单错误怎么呈现。
 */
test.describe("认证", () => {
  test("注册后自动登录，导航栏显示昵称", async ({ page }) => {
    const nickname = `新用户${Date.now().toString(36)}`;

    await signUp(page, { prefix: "signup", nickname });

    await expect(page).toHaveURL("/");
    await expect(page.getByRole("link", { name: nickname })).toBeVisible();
  });

  test("登录后刷新页面仍保持登录", async ({ page }) => {
    const nickname = `刷新${Date.now().toString(36)}`;
    const email = await signUp(page, { prefix: "refresh", nickname });

    // 访问令牌只在内存里，刷新后就没了。能保持登录说明
    // HttpOnly Cookie 里的刷新令牌确实换回了新的访问令牌——
    // 这是整个认证方案最关键、也最容易在重构中悄悄坏掉的一环。
    await page.reload();

    await expect(page.getByRole("link", { name: nickname })).toBeVisible({
      timeout: 15_000,
    });
    expect(email).toBeTruthy();
  });

  test("登出后回到未登录状态", async ({ page }) => {
    const nickname = `登出${Date.now().toString(36)}`;
    await signUp(page, { prefix: "logout", nickname });

    await page.getByRole("button", { name: "登出" }).click();

    await expect(page.getByRole("link", { name: "登录" })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole("link", { name: nickname })).toHaveCount(0);
  });

  test("未登录访问受保护页面会跳转登录并记住原地址", async ({ page }) => {
    await page.goto("/me/registrations");

    await expect(page).toHaveURL(/\/login\?next=%2Fme%2Fregistrations/);
  });

  test("登录后回到原本想访问的页面", async ({ page }) => {
    const nickname = `跳转${Date.now().toString(36)}`;
    const email = await signUp(page, { prefix: "redirect", nickname });

    // 先登出，再从受保护页面被弹到登录页
    await page.getByRole("button", { name: "登出" }).click();
    await expect(page.getByRole("link", { name: "登录" })).toBeVisible();

    await page.goto("/me/registrations");
    await expect(page).toHaveURL(/\/login\?next=/);

    // logIn 内部已经提交了表单，这里不要再点一次
    await logIn(page, email);

    // 登录成功后应当回到 /me/registrations，而不是被丢回首页
    await expect(page).toHaveURL(/\/me\/registrations$/, { timeout: 15_000 });
  });

  test("密码错误显示错误提示而不是空白页", async ({ page }) => {
    const email = await signUp(page, { prefix: "wrongpass", nickname: "错误密码" });
    await page.getByRole("button", { name: "登出" }).click();
    await expect(page.getByRole("link", { name: "登录" })).toBeVisible();

    await logIn(page, email, "definitelyWrong123");

    await expect(formAlert(page)).toContainText("邮箱或密码错误");
  });

  test("注册时弱密码在前端就被拦下，不发请求", async ({ page }) => {
    await page.goto("/register");
    await page.getByLabel("邮箱").fill(uniqueEmail("weak"));
    await page.getByLabel("昵称").fill("弱密码");
    await page.getByLabel("密码").fill("onlyletters");
    await formButton(page, "注册").click();

    await expect(page.getByText("密码需同时包含字母和数字")).toBeVisible();
    // 仍停留在注册页，说明校验确实拦住了
    await expect(page).toHaveURL(/\/register$/);
  });

  test("重复邮箱注册被后端拒绝并显示错误", async ({ page }) => {
    const email = await signUp(page, { prefix: "dup", nickname: "重复" });
    await page.getByRole("button", { name: "登出" }).click();
    await expect(page.getByRole("link", { name: "登录" })).toBeVisible();

    await page.goto("/register");
    await page.getByLabel("邮箱").fill(email);
    await page.getByLabel("昵称").fill("重复2");
    await page.getByLabel("密码").fill(TEST_PASSWORD);
    await formButton(page, "注册").click();

    await expect(formAlert(page)).toContainText("该邮箱已注册");
  });
});
