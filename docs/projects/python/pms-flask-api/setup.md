# Flask API 版搭建指南

本文搭建独立的 Flask REST API。它不内嵌 Vue 页面，可与共享 Vue 前端组合使用，并与 FastAPI、Gin API、Spring Boot API 共用 v2 schema 与 Token 契约。

## 1. 环境与数据库

- Python 3.10+
- MySQL 8.0+，并创建共享库 `pms`
- pip 与 venv

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cd versions/python/pms-flask-api
cp .env.example .env
```

设置数据库连接，以及与需要互认 Token 的 API 相同的 `API_SECRET`。`CORS_ORIGINS` 应填写前端站点 origin。

## 2. 创建 Python 环境

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

## 3. 启动 API 与共享 Vue

```bash
./start.sh
```

Flask 默认监听 `8002`；健康检查为 `/health`。应用首次请求会幂等初始化共享 schema。另开终端启动 Vue：

```bash
cd versions/vue/pms-vue
VITE_API_PROXY_TARGET=http://127.0.0.1:8002 npm run dev
```

Vue 将 `/api` 与 `/health` 请求转发至 Flask。若前后端跨域部署，构建 Vue 时设置 `VITE_API_BASE_URL`，并将前端 origin 加入 `CORS_ORIGINS`。

## 4. 验证

```bash
pip install -r requirements-dev.txt
pytest
curl -i http://127.0.0.1:8002/health
```

API 的端点、输入字段、认证错误和用户隔离规则以REST API 契约为准。

## 5. 关联文档

- 项目入口：[README.md](../../../../versions/python/pms-flask-api/README.md)
- 构建过程：[build-process-explained.md](build-process-explained.md)
- 部署指南：[deployment.md](deployment.md)
