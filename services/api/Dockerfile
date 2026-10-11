# ---------- 构建阶段 ----------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 先只拷 pom 并预下载依赖。这样只要 pom.xml 没变，
# 改业务代码就不会让这一层缓存失效，重建从几分钟降到几秒。
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# 跳过测试：集成测试需要 Testcontainers 拉起 MySQL/Redis/RabbitMQ，
# 构建镜像时既没有 Docker 也不该做这件事。测试在 CI 里跑。
RUN mvn -B -q package -DskipTests

# ---------- 运行阶段 ----------
#
# 用 Debian 基础的 eclipse-temurin 而不是 -alpine 变体：
# Adoptium 的 alpine 镜像只发布 amd64，在 arm64 机器上 build 会直接失败
# （no match for platform in manifest）。多架构可用比省那几十 MB 重要。
FROM eclipse-temurin:17-jre

# curl 是给容器健康检查用的（见 docker-compose.prod.yml 的 healthcheck）。
# 显式安装而不是假设基础镜像自带——镜像版本一变，healthcheck 就会静默失效，
# 而「容器一直显示 unhealthy」是那种要排查很久才能定位到根因的问题。
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 不以 root 运行。容器逃逸时，非特权用户的破坏面小得多，
# 而且这是几乎所有镜像加固基线都会要求的一条。
RUN groupadd --system app && useradd --system --gid app --create-home app

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R app:app /app

USER app
EXPOSE 8080

# 容器内存受限时，JVM 会按宿主机内存自行推算堆大小，容易被 OOM Killer 干掉。
# MaxRAMPercentage 让它尊重容器限额。
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# 用 sh -c 是为了让 $JAVA_OPTS 展开；exec 保证 java 成为 PID 1，
# 从而能收到 docker stop 发来的 SIGTERM 并优雅关闭。
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
