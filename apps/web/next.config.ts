import type { NextConfig } from "next";
import path from "node:path";

const nextConfig: NextConfig = {
  /* config options here */
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },

  // 只打包运行时真正用到的文件，产物不含完整 node_modules。
  // 运行时镜像从 ~700MB 降到 ~150MB。
  output: "standalone",

  // monorepo 下必须显式指定追踪根目录：默认只往上看一层，
  // 会漏掉被提升到仓库根 node_modules 的依赖，产出的 server.js 跑不起来。
  // 构建时 cwd 是 apps/web（npm --workspace 会把 cwd 切到该 workspace），
  // 所以仓库根是它的上两级。
  outputFileTracingRoot: path.join(process.cwd(), "..", ".."),
};

export default nextConfig;
