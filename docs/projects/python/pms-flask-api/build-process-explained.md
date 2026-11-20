# Flask API 版构建过程讲解

该项目是 Flask 版 REST 服务，负责认证、业务字段验证、用户隔离与 v2 MySQL 读写；React 前端仍是独立工程。

## 1. 请求生命周期

```text
React → Flask 路由 → Bearer Token 验签 → 重新加载当前用户
                  → Pydantic 校验请求体
                  → 参数化 SQL / MySQL 事务
                  → 与 API 契约一致的 JSON 响应
```

- `config.py` 从环境变量和 `.env` 加载 DB_*、端口、Token 密钥和 CORS 来源。
- `security.py` 使用 BCrypt 与 HMAC-SHA256；Token claims、签名和 24 小时过期时间与其他 API 版兼容。
- `schemas.py` 复用 FastAPI 使用的 Pydantic 请求模型，限制日期、月份、枚举、时间和自评分格式。
- `db.py` 提供 MySQL DictCursor、类型清洗、幂等 schema 初始化与事务管理。

## 2. 数据和权限边界

- 所有日 / 周 / 月读写均将 `user_id` 与记录 ID 同时作为查询条件；他人记录和不存在的记录均返回 404。
- 管理用户端点额外检查 `is_staff`。
- 时间盒、领域、任务流和系统健康检查在同一数据库事务里整体替换，避免只写入主记录或残缺明细。
- CORS 只为 `CORS_ORIGINS` 中明确列出的 origin 添加响应头；生产配置不要使用通配来源。
- `/health` 用于容器探针；API 仍通过 `/api/auth/login`、`/api/me` 等契约端点交互。

## 3. 构建与验证

Flask API 不需要编译步骤。安装依赖后，Gunicorn 加载 `app:app`；React 可由 Nginx 或静态托管独立发布。

```bash
pip install -r requirements.txt
pip install -r requirements-dev.txt
pytest
```

单元测试无需 MySQL，覆盖 Token 认证入口、输入校验、用户数据边界、CORS 预检及格式错误响应。真实 MySQL 上的联调还需运行 API 契约中的跨版本 CRUD 流程。
