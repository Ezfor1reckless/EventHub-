SHELL := /bin/bash
COMPOSE := docker compose -f infra/docker-compose.yml --env-file .env
# 生产是「基础 + 覆盖」两份文件的组合，顺序不能反：后者覆盖前者
PROD_COMPOSE := docker compose -f infra/docker-compose.yml -f infra/docker-compose.prod.yml --env-file .env

.PHONY: help up down restart logs ps psql redis-cli clean web mobile api test openapi \
        prod-up prod-down prod-logs prod-ps prod-config prod-build init-ssl

help: ## 显示可用命令
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

up: ## 启动基础数据服务（MySQL/Redis/RabbitMQ/Matomo）
	$(COMPOSE) up -d

down: ## 停止并移除容器（保留数据卷）
	$(COMPOSE) down

restart: ## 重启全部服务
	$(COMPOSE) restart

logs: ## 跟踪全部服务日志
	$(COMPOSE) logs -f --tail=100

ps: ## 查看服务状态
	$(COMPOSE) ps

psql: ## 进入业务库 MySQL 命令行
	$(COMPOSE) exec mysql mysql -u$${MYSQL_USER:-eventhub} -p$${MYSQL_PASSWORD} $${MYSQL_DATABASE:-eventhub}

redis-cli: ## 进入 Redis 命令行
	$(COMPOSE) exec redis redis-cli -a $${REDIS_PASSWORD}

clean: ## 停止并删除容器与数据卷（危险：清空所有数据）
	$(COMPOSE) down -v

web: ## 启动 Web 开发服务器
	npm run dev --workspace apps/web

mobile: ## 启动 Expo 开发服务器
	npm run start --workspace apps/mobile

api: ## 启动后端
	cd services/api && ./mvnw spring-boot:run

test: ## 运行全部测试
	npm run test --workspaces --if-present
	cd services/api && ./mvnw verify

openapi: ## 从运行中的应用导出 OpenAPI 到 docs/openapi.yaml（需先 make up && make api）
	@bash scripts/export-openapi.sh

# ---------------- 生产 ----------------

prod-build: ## 构建生产镜像（api / web）
	$(PROD_COMPOSE) build

prod-up: ## 启动生产环境（含 Nginx，需已有证书或先跑 make init-ssl）
	$(PROD_COMPOSE) up -d --build

prod-down: ## 停止生产环境（保留数据卷）
	$(PROD_COMPOSE) down

prod-logs: ## 跟踪生产环境日志
	$(PROD_COMPOSE) logs -f --tail=100

prod-ps: ## 查看生产环境状态
	$(PROD_COMPOSE) ps

prod-config: ## 校验生产编排配置并打印最终结果（不启动任何东西）
	@$(PROD_COMPOSE) config | head -5
	@echo "... (完整输出：$(PROD_COMPOSE) config)"

init-ssl: ## 首次签发 TLS 证书（建议先 STAGING=1 make init-ssl 试跑）
	@bash scripts/init-letsencrypt.sh
