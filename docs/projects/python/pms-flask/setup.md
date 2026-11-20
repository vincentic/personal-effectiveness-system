# Flask 单体版搭建指南

本文搭建独立的 Python Flask 服务端页面应用。该项目不提供 REST API；共享 Vue 前端应连接 Gin API、Spring Boot API、FastAPI 或 Flask API。

## 1. 环境与数据库

- Python 3.10+
- MySQL 8.0+，并创建共享库 `pms`
- pip 与 venv

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cd versions/python/pms-flask
cp .env.example .env
```

`.env` 中填写 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER` 和 `DB_PASSWORD`。初次空库启动时，可用 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 设置管理员。

## 2. 创建环境并安装依赖

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

运行测试时安装开发依赖：

```bash
pip install -r requirements-dev.txt
pytest
```

## 3. 启动

```bash
./start.sh
```

打开 <http://127.0.0.1:8001/>。首次请求会幂等初始化 v2 数据表；当 `users` 表为空时，初始化管理员使用 BCrypt 保存密码。该版本用 Flask 签名 Session 保存登录状态，表单写操作均校验 CSRF Token，清单查询按当前用户隔离。

## 4. 与共享 Vue 前端的关系

Flask 单体版渲染自己的页面，不与 Vue 争用相同路由。若要运行共享 Vue，请启动任一独立 API；例如：

```bash
cd versions/vue/pms-vue
VITE_API_PROXY_TARGET=http://127.0.0.1:8002 npm run dev
```

其中 `8002` 是 Flask API 的默认端口。共享 API 的数据字段和认证约定见[REST API 契约](../../../data/api-contract.md)。

## 5. 关联文档

- 项目入口：[README.md](../../../../versions/python/pms-flask/README.md)
- 构建过程：[build-process-explained.md](build-process-explained.md)
- 部署与 Nginx / Docker / Kubernetes：[deployment.md](deployment.md)
- 共享 schema：[db/schema.sql](../../../../db/schema.sql)
