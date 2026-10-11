/**
 * 把 standalone 产物补齐成可直接运行的形态。
 *
 * `next build` 在 output: "standalone" 下产出的 .next/standalone 里**不含**
 * 静态资源目录 .next/static 和 public/ —— 官方文档要求部署时自己拷过去
 * （Dockerfile 里就是用 COPY 做的同样的事）。少了它们，页面能渲染出 HTML，
 * 但没有样式、也没有客户端 JS，页面无法交互。
 *
 * 本地跑 E2E 时用这个脚本补齐，好处是测试跑的就是生产部署的那个 server，
 * 而不是一个只在本地成立的 next start。
 */
import { cp, access } from "node:fs/promises";
import path from "node:path";

const standaloneDir = path.join(".next", "standalone", "apps", "web");

async function exists(target) {
  try {
    await access(target);
    return true;
  } catch {
    return false;
  }
}

if (!(await exists(standaloneDir))) {
  console.error(
    `找不到 standalone 产物：${standaloneDir}\n先执行 npm run build（需 next.config.ts 里开启 output: "standalone"）。`,
  );
  process.exit(1);
}

await cp(".next/static", path.join(standaloneDir, ".next", "static"), {
  recursive: true,
});

if (await exists("public")) {
  await cp("public", path.join(standaloneDir, "public"), { recursive: true });
}

console.log(`standalone 产物已补齐：${standaloneDir}`);
