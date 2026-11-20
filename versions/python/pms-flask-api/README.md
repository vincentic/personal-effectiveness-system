# PMS Flask API 版

Flask API 版提供个人效能系统 v2 的 REST API，可直接连接仓库中的共享 Vue 前端。它与 FastAPI、Gin API 和 Spring Boot API 共用 MySQL schema、BCrypt 密码格式、HMAC-SHA256 Bearer Token 与 `/api/**` 契约。

## 快速开始

要求 Python 3.10+ 和 MySQL 8.0+。先创建共享数据库并复制环境配置：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cd versions/python/pms-flask-api
cp .env.example .env
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./start.sh
```

应用启动时会幂等初始化 v2 schema；只有 `users` 表为空时才创建 `.env` 中的默认管理员。开发 API 地址为 <http://127.0.0.1:8002/>，健康检查为 `/health`。

在 `versions/vue/pms-vue/` 启动共享前端时，将 API 代理目标设为 Flask：

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8002 npm run dev
```

共享 API 的请求/响应字段、权限边界和错误格式见[REST API 契约](../../../docs/data/api-contract.md)；项目的安装、构建和生产部署步骤见[搭建指南](../../../docs/projects/python/pms-flask-api/setup.md)、[构建过程讲解](../../../docs/projects/python/pms-flask-api/build-process-explained.md)和[部署指南](../../../docs/projects/python/pms-flask-api/deployment.md)。

## 配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `127.0.0.1:3306` | MySQL 地址 |
| `DB_NAME` | `pms` | 共享数据库 |
| `DB_USER` / `DB_PASSWORD` | `root` / 空 | 数据库账号 |
| `SERVER_PORT` | `8002` | Flask 开发服务端口 |
| `API_SECRET` | 开发占位值 | 生产必须替换；跨 API 互认 Token 时需与其他 API 相同 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | 仅在 users 表为空时初始化管理员 |
| `CORS_ORIGINS` | 本地 Vite origins | 逗号分隔的允许来源；生产应限制为实际前端 origin |

## 验证

```bash
pip install -r requirements-dev.txt
pytest
```

生产运行使用 Gunicorn，不要启用 Flask debug server。部署文档包含 Nginx、Docker 与 Kubernetes 操作步骤。
