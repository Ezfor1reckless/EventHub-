import { expect, test } from "@playwright/test";

import { createPublishedEvent, signUp, toDateTimeLocal } from "./helpers";

/**
 * 业务闭环的端到端验证：组织者发布 → 用户报名 → 票券 → 候补递补。
 *
 * 业务规则本身在 API 层已有充分覆盖（见 AuthFlowIT / RegistrationFlowIT），
 * 这里验证的是这些规则经由真实界面走通——表单能提交、状态能刷新、
 * 跨页面的数据是一致的。
 */

test("组织者创建并发布活动后，活动出现在公开列表", async ({ page }) => {
  await signUp(page, { prefix: "org", nickname: "组织者" });
  const title = await createPublishedEvent(page, { capacity: 3 });

  await page.goto("/events");
  await expect(page.getByRole("heading", { name: title })).toBeVisible({
    timeout: 15_000,
  });
});

test("用户报名后在「我的报名」里看到票券二维码", async ({ browser }) => {
  // 组织者和报名者用两个独立的浏览器上下文。
  // 用「清 Cookie + 刷新」来切换用户是可以的，但很脆：
  // 访问令牌在内存里，清 Cookie 之后还得靠一次完整的重新加载才会真正重置状态。
  // 开新上下文更接近真实的多用户场景，也不会受上一个用户残留状态的影响。
  const organizerContext = await browser.newContext();
  const joinerContext = await browser.newContext();

  try {
    const organizerPage = await organizerContext.newPage();
    const joinerPage = await joinerContext.newPage();

    await signUp(organizerPage, { prefix: "org", nickname: "组织者" });
    const title = await createPublishedEvent(organizerPage, { capacity: 5 });

    await signUp(joinerPage, { prefix: "joiner", nickname: "报名者" });

    await joinerPage.goto("/events");
    await joinerPage.getByRole("heading", { name: title }).click();
    await expect(joinerPage).toHaveURL(/\/events\/\d+$/);

    await joinerPage.getByRole("button", { name: "立即报名" }).click();
    await expect(joinerPage.getByText("已确认")).toBeVisible({ timeout: 15_000 });

    await joinerPage.goto("/me/registrations");
    const row = joinerPage.locator("li", { hasText: title });
    await expect(row.getByText("已确认")).toBeVisible();

    await row.getByRole("button", { name: "查看票券" }).click();
    await expect(row.getByText("票券码")).toBeVisible();
    // 票券码是 12 位 Crockford Base32（去掉了容易混淆的 I/L/O/U）
    await expect(row.locator("p.font-mono")).toHaveText(/^[0-9A-HJKMNP-TV-Z]{12}$/);
  } finally {
    await organizerContext.close();
    await joinerContext.close();
  }
});

test("名额已满时报名进入候补，有人取消后自动递补", async ({ browser }) => {
  const organizerContext = await browser.newContext();
  const firstContext = await browser.newContext();
  const secondContext = await browser.newContext();

  try {
    const organizerPage = await organizerContext.newPage();
    const firstPage = await firstContext.newPage();
    const secondPage = await secondContext.newPage();

    // 容量 1：第一个人占满，第二个人只能候补
    await signUp(organizerPage, { prefix: "org", nickname: "组织者" });
    const title = await createPublishedEvent(organizerPage, { capacity: 1 });

    await signUp(firstPage, { prefix: "first", nickname: "先到" });
    await firstPage.goto("/events");
    await firstPage.getByRole("heading", { name: title }).click();
    await firstPage.getByRole("button", { name: "立即报名" }).click();
    await expect(firstPage.getByText("已确认")).toBeVisible({ timeout: 15_000 });

    await signUp(secondPage, { prefix: "second", nickname: "候补" });
    await secondPage.goto("/events");
    await secondPage.getByRole("heading", { name: title }).click();
    await secondPage.getByRole("button", { name: "立即报名" }).click();
    await expect(secondPage.getByText("候补中")).toBeVisible({ timeout: 15_000 });

    // 候补者不能签到，界面上也应体现为「取消候补」而不是「取消报名」
    await secondPage.goto("/me/registrations");
    const secondRow = secondPage.locator("li", { hasText: title });
    await expect(secondRow.getByText("候补中")).toBeVisible();
    await expect(secondRow.getByRole("button", { name: "取消候补" })).toBeVisible();

    // 先到者取消，名额空出，候补队列的队首应当被自动递补
    await firstPage.goto("/me/registrations");
    const firstRow = firstPage.locator("li", { hasText: title });
    await firstRow.getByRole("button", { name: "取消报名" }).click();
    await expect(firstRow.getByText("已取消")).toBeVisible({ timeout: 15_000 });

    // 递补是在后端同一个事务里完成的，刷新就能看到
    await secondPage.reload();
    const promotedRow = secondPage.locator("li", { hasText: title });
    await expect(promotedRow.getByText("已确认")).toBeVisible({ timeout: 15_000 });
    await expect(promotedRow.getByRole("button", { name: "查看票券" })).toBeVisible();
  } finally {
    await organizerContext.close();
    await firstContext.close();
    await secondContext.close();
  }
});

test("草稿活动不出现在公开列表，发布后才出现", async ({ page }) => {
  await signUp(page, { prefix: "org", nickname: "组织者" });

  const title = `草稿活动-${Date.now().toString(36)}`;
  await page.goto("/organizer/events/new");
  await page.getByLabel("活动标题").fill(title);
  await page.getByLabel("活动地点").fill("草稿场地");
  await page.getByLabel("报名人数上限").fill("5");
  const start = new Date(Date.now() + 30 * 24 * 3600 * 1000);
  await page.getByLabel("开始时间").fill(toDateTimeLocal(start));
  await page.getByLabel("结束时间").fill(toDateTimeLocal(new Date(start.getTime() + 3600_000)));
  await page.getByRole("button", { name: "创建为草稿" }).click();
  await expect(page).toHaveURL(/\/organizer\/events$/);

  // 草稿不该出现在公开列表里
  await page.goto("/events");
  await expect(page.getByRole("heading", { name: title })).toHaveCount(0);

  // 发布后出现
  await page.goto("/organizer/events");
  const row = page.locator("li", { hasText: title });
  await row.getByRole("button", { name: "发布" }).click();
  await expect(row.getByText("报名中")).toBeVisible({ timeout: 15_000 });

  await page.goto("/events");
  await expect(page.getByRole("heading", { name: title })).toBeVisible({
    timeout: 15_000,
  });
});
