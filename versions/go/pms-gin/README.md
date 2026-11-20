# PMS 个人效能系统 · Gin 版

基于 **Go + Gin + MySQL** 的「个人效能系统」实现，围绕三张可手写打印的清单：
**每日执行清单 / 每周复盘清单 / 每月重构清单**。与 Django / Spring Boot / FastAPI 版共用
`db/schema.sql` 的 v2 表（`users` + `daily_plans`/`time_blocks` +
`weekly_reviews`/`weekly_domain_reviews`/`weekly_task_flows` +
`monthly_reviews`/`system_health_checks`）、BCrypt 密码与同一套 API Token，
因此**同一个库、同一套账号、同一个 Token 可在各版本间互通**。本版同时提供服务端渲染页面与 `/api/**` REST API。

界面为中文，使用 Bootstrap 5.3.3（CDN）+ 自定义 `static/css/style.css`。默认端口 `8081`。

---

## 1. 快速开始

前置条件：**Go 1.22+** 与 **MySQL 8.0+**。

```sql
CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
cp .env.example .env                 # 按需修改 DB_*、SESSION_SECRET、API_SECRET
export GOPROXY=https://goproxy.cn,direct
go mod tidy
go run .                             # 或 ./start.sh
```

浏览器打开 <http://127.0.0.1:8081/login>，默认管理员 `admin / admin123`。
启动时以 `multiStatements=true` 幂等执行 `sql/schema.sql`（已同步为 v2）；`users` 为空则创建默认管理员。

---

## 2. 环境变量

`internal/config` 内置极简 `.env` 加载器，**系统环境变量优先**。

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_NAME` | `pms` | 数据库名 |
| `DB_USER` | `root` | 数据库账号 |
| `DB_PASSWORD` | 空 | 数据库密码 |
| `DB_HOST` | `127.0.0.1` | 数据库地址 |
| `DB_PORT` | `3306` | 数据库端口 |
| `SERVER_PORT` | `8081` | 服务监听端口 |
| `SESSION_SECRET` | `dev-insecure-session-secret-change-me` | 会话 Cookie 签名密钥 |
| `API_SECRET` | `dev-insecure-api-secret-change-me` | API Token 签名密钥（三版一致才互认） |
| `CORS_ORIGINS` | `http://localhost:5173,http://127.0.0.1:5173` | 允许访问 API 的 Vue 站点 origin，多个用逗号分隔 |
| `ADMIN_USERNAME` | `admin` | 首次启动创建的管理员 |
| `ADMIN_PASSWORD` | `admin123` | 首次启动创建的管理员密码 |
| `COOKIE_SECURE` | `false` | Cookie 是否仅 HTTPS 传输 |

---

## 3. 页面与路由

所有数据按当前登录用户隔离；页面表单带 `_csrf`（`pms_csrf` double submit），写操作走 PRG。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET / POST | `/login` | 登录 |
| POST | `/logout` | 退出 |
| GET | `/` | 工作台（三类清单统计 + 今日/本周/本月 + 最近记录） |
| GET | `/daily/` | 每日清单列表 |
| GET / POST | `/daily/new/` | 新建日清单（含时间盒明细） |
| GET | `/daily/:id/` | 日清单详情 |
| GET / POST | `/daily/:id/edit/` | 编辑日清单 |
| GET | `/daily/:id/delete/` | 删除日清单（级联时间盒） |
| GET | `/weekly/` | 每周复盘列表 |
| GET / POST | `/weekly/new/` | 新建周复盘（含领域 4 行 + 任务流） |
| GET | `/weekly/:id/` | 周复盘详情 |
| GET / POST | `/weekly/:id/edit/` | 编辑周复盘 |
| GET | `/weekly/:id/delete/` | 删除周复盘 |
| GET | `/monthly/` | 每月重构列表 |
| GET / POST | `/monthly/new/` | 新建月重构（含健康 6 项） |
| GET | `/monthly/:id/` | 月重构详情 |
| GET / POST | `/monthly/:id/edit/` | 编辑月重构 |
| GET | `/monthly/:id/delete/` | 删除月重构 |
| GET / POST | `/admin/users/` | 用户管理（仅管理员） |

> gin 路由树不允许同一层既有静态段又有通配段，故 `/xxx/new/` 合并进 `/xxx/:id/` 内部分流（`id == "new"`）。

---

## 4. REST API

契约详见 `db/API.md`。除 `/api/auth/login` 外均需 `Authorization: Bearer <token>`；
统一错误体 `{"detail":"..."}`，状态码 400 / 401 / 403 / 404 / 201 / 204。API 路由豁免 CSRF。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 公开 | `{username,password}` → `{token, user}` |
| GET | `/api/me` | Bearer | 当前用户 |
| GET | `/api/dashboard` | Bearer | 三类清单统计 + 今日/本周/本月 + 最近记录 + 近 7 日组块汇总 |
| GET / POST | `/api/daily` | Bearer | 我的日清单列表 / 新建（含 blocks） |
| GET / PUT / DELETE | `/api/daily/:id` | Bearer | 详情 / 更新（blocks 整体替换）/ 删除 |
| GET / POST | `/api/weekly` | Bearer | 我的周复盘列表 / 新建（含 domains/flows） |
| GET / PUT / DELETE | `/api/weekly/:id` | Bearer | 详情 / 更新 / 删除 |
| GET / POST | `/api/monthly` | Bearer | 我的月重构列表 / 新建（含 health_checks） |
| GET / PUT / DELETE | `/api/monthly/:id` | Bearer | 详情 / 更新 / 删除 |
| GET / POST | `/api/users` | Bearer + 管理员 | 用户列表 / 创建用户 |

所有清单仅本人可读写，跨用户一律 **404**；用户管理仅 `is_staff` 管理员（非管理员 **403**）。
`user` 响应不含密码哈希。所有返回字段为 snake_case，且带读时计算：
日清单 `blocks_total/blocks_done/block_progress/checklist_total(13)/checklist_done`；
周复盘 `week_end(=week_start+6天)/domains/flows`；月重构 `health_checks/avg_score`。

明细子表（时间盒 / 领域 / 任务流 / 健康检查）随主表在**同一事务内整体替换**（先删后插）。

共享 Vue 源码位于 `versions/python/pms-fastapi/frontend`。在该目录运行
`VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev` 可直接使用 Gin API；
若 Vue 与 Gin 分开部署，将前端 origin 配入 `CORS_ORIGINS`。

### Token 格式（自实现 HMAC-SHA256，无第三方 JWT 依赖）

```
claims  = {"uid":1,"username":"admin","staff":true,"exp":1799999999}
payload = base64url(JSON(claims))          // 无填充、紧凑序列化
sig     = hex(HMAC_SHA256(API_SECRET, payload))
token   = payload + "." + sig
```

验签只覆盖**收到的 payload 串**、不重新序列化 claims；使用 `crypto/subtle.ConstantTimeCompare`
常量时间比较并校验 `exp > now`。页面登录走独立签名 Cookie `pms_session`，与 API Token 相互独立。

---

## 5. curl 示例

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s http://127.0.0.1:8081/api/dashboard -H "Authorization: Bearer $TOKEN"

curl -s -X POST http://127.0.0.1:8081/api/daily \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"plan_date":"2026-11-17","energy_level":"high","mit":"完成迁移","blocks":[{"seq":1,"block_name":"改造三版","is_done":true}]}'

curl -s http://127.0.0.1:8081/api/weekly -H "Authorization: Bearer $TOKEN"
curl -s http://127.0.0.1:8081/api/monthly -H "Authorization: Bearer $TOKEN"
```

---

## 6. 目录结构

```
versions/go/pms-gin/
├── main.go                       # 入口：go:embed、建表、启动
├── sql/schema.sql                # v2 共享建表 SQL（与 db/schema.sql 一致）
├── static/css/style.css          # 自定义样式
├── templates/                    # base.html 布局；daily/weekly/monthly 各 list/detail/form
├── internal/
│   ├── config/config.go          # .env 加载与配置
│   ├── database/database.go      # 连接、建表、默认管理员、通用 rows 扫描、BCrypt
│   ├── models/models.go          # v2 领域模型、In 请求体、Out 视图、读时计算、枚举
│   ├── models/store.go           # v2 日/周/月 CRUD，明细事务整体替换
│   └── server/
│       ├── auth.go               # 会话 Cookie、CSRF、HMAC 工具（与 v1 兼容，勿改）
│       ├── render.go             # 每页独立 template set、FuncMap、渲染
│       ├── handlers.go           # 页面处理与表单映射
│       ├── api.go                # REST API 与 Token
│       └── router.go             # 页面 / API 路由与中间件
└── .env.example  start.sh  README.md
```

---

## 7. 注意事项

- `SESSION_SECRET` / `API_SECRET` 为开发占位值，生产环境必须替换并开启 `COOKIE_SECURE=true`。
- Bootstrap 5 的 CSS / JS 来自 jsDelivr CDN，离线部署需改本地静态资源。
- 清单列表暂未分页；工作台最近记录各取 5 条。
- 数据库时区与应用时区需一致：`today_plan` / `week_review` / `month_review` 由数据库当前日期判定。
