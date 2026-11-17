# PMS FastAPI 版

纯 REST API 实现，与 Spring Boot 版、Gin 版共用同一套数据库表（`db/schema.sql`）和 API 契约（`db/API.md`），Token 三版互通。

## 启动

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

交互式文档：<http://127.0.0.1:8000/docs>

## 配置

复制 `.env.example` 为 `.env`，主要变量：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_HOST` / `DB_PORT` | `127.0.0.1:3306` | MySQL 地址 |
| `DB_NAME` | `pms` | 数据库名 |
| `DB_USER` / `DB_PASSWORD` | `root` / 空 | 数据库账号 |
| `API_SECRET` | `dev-insecure-api-secret-change-me` | Token 签名密钥，与另两版保持一致 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | 首次启动默认管理员 |
| `CORS_ORIGINS` | `*` | 允许的跨域来源 |

启动时自动执行 `sql/schema.sql` 幂等建表；users 表为空时自动创建默认管理员。

## API

见 `../db/API.md`。端点包括：

```
POST /api/auth/login
GET  /api/me
GET  /api/dashboard
GET|POST     /api/projects
GET|PUT|DELETE /api/projects/{id}
POST /api/projects/{id}/tasks
PUT|DELETE   /api/tasks/{id}
GET|POST     /api/users
```

## 测试记录

- 登录返回 Token：✅
- 三版 Token 交叉验证（9 组合）：✅
- 跨版本 CRUD：✅
