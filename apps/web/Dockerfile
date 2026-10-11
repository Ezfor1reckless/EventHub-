# 构建上下文是仓库根目录（见 infra/docker-compose.prod.yml 的 build.context），
# 因为这是 npm workspaces 的 monorepo，lockfile 在根目录。

# ---------- 构建阶段 ----------
FROM node:20-alpine AS builder

# 必须用 npm 11 执行 npm ci。
# npm 10 处理根 package.json 里的 overrides 与 lockfile 的方式和 npm 11 不同，
# 用镜像自带的 npm 10.8 会报 "Missing: @types/react@19.2.18 from lock file" ——
# 而 lockfile 本身是完好的，同一个文件在 npm 11 下能正常装。
# 显式升级而不是依赖某个特定 Node 版本自带 npm 11，是为了让基础镜像可以自由更换。
RUN npm install -g npm@11

WORKDIR /app

# 只拷 package.json 再装依赖：只要依赖没变，改业务代码就不会让这一层缓存失效。
#
# 装的是整个 monorepo（含移动端依赖），而不是 --workspace apps/web。
# 原因：根 package.json 里的 overrides 与 workspace 过滤后的依赖解析结果对不上，
# npm ci --workspace 会报 "Missing: @types/react from lock file"。
#
# 这不会让最终镜像变大——builder 阶段的内容只有被 COPY 进 runner 的才会进镜像，
# 而 runner 只拿 standalone 产物。代价仅是构建时间。
COPY package.json package-lock.json ./
COPY apps/web/package.json ./apps/web/
COPY apps/mobile/package.json ./apps/mobile/
RUN npm ci

COPY apps/web ./apps/web
RUN npm run build --workspace apps/web

# ---------- 运行阶段 ----------
FROM node:24-alpine AS runner

RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
# 必须绑 0.0.0.0：默认只监听 localhost 的话，容器外部（包括 Nginx）连不上
ENV HOSTNAME=0.0.0.0

# standalone 产物已内含精简过的 node_modules 和 server.js，
# 不需要把整个 node_modules 拷进来。
COPY --from=builder --chown=app:app /app/apps/web/.next/standalone ./
COPY --from=builder --chown=app:app /app/apps/web/.next/static ./apps/web/.next/static
COPY --from=builder --chown=app:app /app/apps/web/public ./apps/web/public

USER app
EXPOSE 3000

CMD ["node", "apps/web/server.js"]
