import { expect, type Page } from "@playwright/test";

export const TEST_PASSWORD = "s3cretPass1";

/**
 * 每次都生成全新的邮箱。
 *
 * 测试共用一个数据库，写死邮箱会让第二次运行直接撞上「该邮箱已注册」——
 * 那种失败看起来像功能坏了，实际上只是测试自己不干净。
 */
export function uniqueEmail(prefix: string): string {
  const suffix = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
  return `${prefix}-${suffix}@example.com`;
}

/**
 * 表单内的提交按钮。
 *
 * 必须限定在 <main> 里：导航栏在 <main> 之外，但里面也有「登录」「注册」两个
 * 链接按钮。不限定作用域时 getByRole 会同时命中导航栏和表单里的按钮，
 * Playwright 的 strict mode 会直接报错拒绝执行——这其实是好事，
 * 否则点错元素会变成更难查的间歇性失败。
 */
export function formButton(page: Page, name: string) {
  return page.getByRole("main").getByRole("button", { name });
}

/**
 * 表单里的错误提示。
 *
 * 同样必须限定在 <main> 内：Next.js 会往 body 末尾插入一个
 * role="alert" 的路由播报器（#__next-route-announcer__），
 * 不限定作用域时 getByRole("alert") 会同时命中它和错误提示框，
 * 断言就会随机命中那个空元素，表现为「偶发失败」。
 */
export function formAlert(page: Page) {
  return page.getByRole("main").getByRole("alert");
}

/** 通过界面注册并登录一个全新用户。 */
export async function signUp(
  page: Page,
  options: { prefix: string; nickname?: string },
): Promise<string> {
  const email = uniqueEmail(options.prefix);

  await page.goto("/register");
  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("昵称").fill(options.nickname ?? options.prefix);
  await page.getByLabel("密码").fill(TEST_PASSWORD);
  await formButton(page, "注册").click();

  // 注册成功后跳回首页，导航栏出现昵称
  await expect(page.getByRole("link", { name: options.nickname ?? options.prefix })).toBeVisible({
    timeout: 15_000,
  });

  return email;
}

/** 用已有账号登录。会填表并提交，调用方不要再点一次提交按钮。 */
export async function logIn(page: Page, email: string, password = TEST_PASSWORD): Promise<void> {
  // 已经在登录页时不要重新导航。
  // 从受保护页面被弹过来时 URL 上带着 ?next=<原地址>，重新 goto("/login")
  // 会把这个参数冲掉，登录后就被丢回首页——正是「登录后回到原本想访问的页面」
  // 那个用例要验证的行为，这么做等于把被测行为改掉了。
  if (!page.url().includes("/login")) {
    await page.goto("/login");
  }

  await page.getByLabel("邮箱").fill(email);
  await page.getByLabel("密码").fill(password);
  await formButton(page, "登录").click();
}

/**
 * 创建一个活动并发布，返回活动标题。
 *
 * 标题带唯一后缀：活动列表是全局的，固定标题会让断言分不清
 * 自己创建的是哪一个（尤其列表里已经有上一次运行留下的数据）。
 */
export async function createPublishedEvent(
  page: Page,
  options: { capacity?: number; prefix?: string } = {},
): Promise<string> {
  const title = `${options.prefix ?? "E2E 活动"}-${Date.now().toString(36)}`;

  await page.goto("/organizer/events/new");

  await page.getByLabel("活动标题").fill(title);
  await page.getByLabel("活动地点").fill("E2E 测试场地");
  await page.getByLabel("报名人数上限").fill(String(options.capacity ?? 2));

  // datetime-local 只接受 YYYY-MM-DDTHH:mm，不能带秒或时区
  const start = new Date(Date.now() + 30 * 24 * 3600 * 1000);
  const end = new Date(start.getTime() + 2 * 3600 * 1000);
  await page.getByLabel("开始时间").fill(toDateTimeLocal(start));
  await page.getByLabel("结束时间").fill(toDateTimeLocal(end));

  await page.getByRole("button", { name: "创建为草稿" }).click();
  await expect(page).toHaveURL(/\/organizer\/events$/, { timeout: 15_000 });

  // 草稿要发布后别人才能看到并报名
  const row = page.locator("li", { hasText: title });
  await row.getByRole("button", { name: "发布" }).click();
  await expect(row.getByText("报名中")).toBeVisible({ timeout: 15_000 });

  return title;
}

export function toDateTimeLocal(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}
