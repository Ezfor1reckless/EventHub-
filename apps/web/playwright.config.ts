import { defineConfig, devices } from "@playwright/test";

/**
 * 端到端测试配置。
 *
 * 前提：后端（含 MySQL/Redis/RabbitMQ）与前端都在跑。
 * 本地执行见 README；CI 里由 workflow 负责拉起依赖。
 */
export default defineConfig({
  testDir: "./e2e",

  // 串行执行。这些测试共用同一个后端与数据库，并行会互相干扰：
  // 活动列表是全局的，一个测试刚发布的活动会影响另一个测试对列表的断言。
  // 用唯一邮箱能让用户互不冲突，但活动计数、候补队列这些没法隔离。
  fullyParallel: false,
  workers: 1,

  forbidOnly: Boolean(process.env.CI),
  // 本地不重试：失败就是失败，重试会掩盖真实的时序问题。
  // CI 上重试一次，用来过滤基础设施抖动，而不是掩盖代码缺陷。
  retries: process.env.CI ? 1 : 0,

  reporter: process.env.CI
    ? [["html", { open: "never" }], ["list"]]
    : [["list"]],

  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    // 失败时留下现场：trace 能在时间轴上回放每一步，比日志好排查得多
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },

  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],

  webServer: {
    // 刻意跑 standalone server（生产部署用的就是它），而不是 next start。
    //
    // 两个原因：一是 Next 明确警告 next start 不支持 standalone 产物，
    // 今天能跑不等于下个版本还能；二是更根本的——用 next start 测出来的结果
    // 属于另一个产物，生产上出问题的地方测试根本覆盖不到。
    //
    // 需要先 npm run build；CI 里 build 在前面的步骤已完成。
    command: "npm run start:standalone",
    url: "http://localhost:3000",
    // 本地已有 dev server 时复用它，不必再起一个生产服务
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    stdout: "pipe",
  },
});
