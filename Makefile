SHELL := /bin/bash
COMPOSE := docker compose -f infra/docker-compose.yml --env-file .env

.PHONY: help up down restart logs ps psql redis-cli clean web mobile api test

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
