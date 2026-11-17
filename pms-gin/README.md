# PMS 项目管理系统 · Gin 版

基于 **Go 1.22 + Gin + MySQL** 的轻量级项目与任务管理系统。与 Django / Spring Boot / FastAPI 版共用
`db/schema.sql` 的四张表（`users / projects / project_members / tasks`）、BCrypt 密码与同一套 API Token，
因此**同一个库、同一套账号、同一个 Token 可在各版本间互通**。本版同时提供服务端渲染页面与 `/api/**` REST API。

界面为中文，使用 Bootstrap 5.3.3（CDN）+ 自定义 `static/css/style.css`。默认端口 `8081`。

---

## 1. 快速开始

前置条件：已安装 **Go 1.22+** 与 **MySQL 8.0+**。

**① 创建数据库**（MySQL 客户端执行）

```sql
CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

**② 配置环境变量**

```bash
cp .env.example .env
# 按需修改 DB_*、SESSION_SECRET、API_SECRET
```

**③ 整理依赖**（国内网络务必设置 GOPROXY）

```bash
export GOPROXY=https://goproxy.cn,direct
go mod tidy
```

> `go mod tidy` 会生成 `go.sum` 并拉取 `gin v1.10.0`、`go-sql-driver/mysql v1.9.0`、
> `golang.org/x/crypto v0.31.0` 及其间接依赖。本仓库不预置 `go.sum`（构建机无 Go 工具链）。

**④ 启动**

```bash
go run .
# 或
chmod +x start.sh && ./start.sh
```

浏览器打开 <http://127.0.0.1:8081/login>，使用默认管理员 `admin / admin123` 登录。

> 启动时会以 `multiStatements=true` 一次 `Exec` 执行 `sql/schema.sql` 幂等建表；
> 若 `users` 表为空，则用 BCrypt 生成默认管理员（可用 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 覆盖）。

---

## 2. 环境变量

`internal/config` 内置极简 `.env` 加载器（每行 `KEY=VALUE`，`#` 为注释），
**系统环境变量优先于 `.env` 文件**。

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_NAME` | `pms` | 数据库名 |
| `DB_USER` | `root` | 数据库账号 |
| `DB_PASSWORD` | 空 | 数据库密码 |
| `DB_HOST` | `127.0.0.1` | 数据库地址 |
| `DB_PORT` | `3306` | 数据库端口 |
| `SERVER_PORT` | `8081` | 服务监听端口 |
| `SESSION_SECRET` | `dev-insecure-session-secret-change-me` | 会话 Cookie 签名密钥，生产必须更换 |
| `API_SECRET` | `dev-insecure-api-secret-change-me` | API Token 签名密钥，三版一致才能互认 |
| `ADMIN_USERNAME` | `admin` | 首次启动创建的管理员账号 |
| `ADMIN_PASSWORD` | `admin123` | 首次启动创建的管理员密码 |
| `COOKIE_SECURE` | `false` | 会话 / CSRF Cookie 是否仅 HTTPS 传输 |

---

## 3. 页面与路由

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET / POST | `/login` | 公开 | 登录（已登录跳转工作台） |
| POST | `/logout` | 登录 | 退出 |
| GET | `/` | 登录 | 工作台（统计概览） |
| GET | `/projects/` | 登录 | 项目列表，支持 `?status=` 与 `?q=` |
| GET / POST | `/projects/new/` | 登录 | 新建项目（创建者自动为负责人） |
| GET | `/projects/:id/` | 登录 | 项目详情 + 三列任务看板 |
| GET / POST | `/projects/:id/edit/` | 负责人 | 编辑项目 |
| GET | `/projects/:id/delete/` | 负责人 | 删除项目（级联任务） |
| GET / POST | `/projects/:id/tasks/new/` | 登录 | 新建任务 |
| GET / POST | `/tasks/:id/edit/` | 登录 | 编辑任务 |
| GET | `/tasks/:id/delete/` | 登录 | 删除任务 |
| GET / POST | `/admin/users/` | 管理员 | 用户列表与新增用户 |

未登录访问页面 302 → `/login`；项目编辑 / 删除仅负责人（否则 302 回详情）；任务写操作仅要求登录。
所有写操作遵循 **POST → 校验 → 落库 → 重定向（PRG）**；页面表单均带 hidden `_csrf`，
服务端以 `pms_csrf` Cookie 做 double submit 校验，失败返回 403。`/api/**` 豁免 CSRF。

---

## 4. REST API

契约详见 `db/API.md`。除 `/api/auth/login` 外，所有 `/api/**` 均需
`Authorization: Bearer <token>` 请求头；统一错误体 `{"detail":"..."}`，状态码 400 / 401 / 403 / 404 / 201 / 204。

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 公开 | `{username,password}` → `{token, user}` |
| GET | `/api/me` | Bearer | 当前用户 |
| GET | `/api/dashboard` | Bearer | 全局统计 + 我的项目(5) + 我的待办(10) |
| GET / POST | `/api/projects` | Bearer | 列表（支持 `?status=&q=`）/ 创建 |
| GET / PUT / DELETE | `/api/projects/:id` | Bearer（写仅负责人） | 详情 / 更新 / 删除 |
| POST | `/api/projects/:id/tasks` | Bearer | 新建任务 |
| PUT / DELETE | `/api/tasks/:id` | Bearer | 更新 / 删除任务 |
| GET / POST | `/api/users` | Bearer + 管理员 | 用户列表 / 创建用户 |

`user` 响应只包含 `id / username / display_name / is_staff / created_at`，**绝不含密码哈希**；
项目 `progress = round(done_task_count / task_count * 100)` 读时计算；非负责人改项目返回 403，
非管理员操作用户返回 403。

### Token 格式（自实现 HMAC-SHA256，无第三方 JWT 依赖）

```
claims  = {"uid":1,"username":"admin","staff":true,"exp":1799999999}
payload = base64url(JSON(claims))          // 不带填充、紧凑序列化
sig     = hex(HMAC_SHA256(API_SECRET, payload))
token   = payload + "." + sig
```

验签只覆盖**收到的 payload 串**、不重新序列化 claims，故字段顺序无关紧要；
使用 `crypto/subtle.ConstantTimeCompare` 常量时间比较，并校验 `exp > now`。
页面登录走独立的签名 Cookie `pms_session`（载荷 `uid|username|staff|exp`），与 API Token 相互独立。

---

## 5. curl 示例

```bash
# 登录取 Token
TOKEN=$(curl -s -X POST http://127.0.0.1:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# 当前用户
curl -s http://127.0.0.1:8081/api/me -H "Authorization: Bearer $TOKEN"

# 工作台统计
curl -s http://127.0.0.1:8081/api/dashboard -H "Authorization: Bearer $TOKEN"

# 创建项目
curl -s -X POST http://127.0.0.1:8081/api/projects \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"官网改版","description":"首页重构","status":"active","member_ids":[]}'

# 项目详情（含 tasks / member_ids / progress）
curl -s http://127.0.0.1:8081/api/projects/1 -H "Authorization: Bearer $TOKEN"

# 新建任务
curl -s -X POST http://127.0.0.1:8081/api/projects/1/tasks \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"设计稿评审","status":"todo","priority":"high"}'

# 筛选项目
curl -s 'http://127.0.0.1:8081/api/projects?status=active&q=官网' -H "Authorization: Bearer $TOKEN"
```

---

## 6. 功能对照表

| 能力 | Django 4.2 | Spring Boot | FastAPI | Gin（本版） |
|------|:----------:|:-----------:|:-------:|:-----------:|
| 服务端渲染中文页面 | ✓ | ✓ | — | ✓ |
| `/api/**` REST API | — | ✓ | ✓ | ✓ |
| 共用 `users/projects/tasks` 表与 BCrypt | 独立表 | ✓ | ✓ | ✓ |
| 三版互通 API Token | — | ✓ | ✓ | ✓ |
| 页面登录态 | Django Session | Spring Security | — | 签名 Cookie `pms_session` |
| CSRF 防护 | Django CSRF | Spring Security | — | `pms_csrf` double submit |
| 模板引擎 | Django Templates | Thymeleaf | — | `html/template` |
| 默认端口 | 8000 | 8080 | 8000 | **8081** |
| 用户管理后台 | Django Admin | `/admin/users/` | `/api/users` | `/admin/users/` + `/api/users` |
| 进度读时计算 | ✓ | ✓ | ✓ | ✓ |

---

## 7. 目录结构

```
pms-gin/
├── main.go                         # 入口：go:embed、建表、启动
├── go.mod                          # 三个直接依赖
├── sql/schema.sql                  # 共享建表 SQL（保持不动）
├── static/css/style.css            # 自定义样式（保持不动）
├── templates/                      # base.html 定义布局与 taskCards 片段
│   ├── base.html   login.html   dashboard.html
│   ├── project_list.html   project_detail.html
│   └── project_form.html   task_form.html   admin_users.html
├── internal/
│   ├── config/config.go            # .env 加载与配置
│   ├── database/database.go        # 连接、建表、默认管理员、通用 rows 扫描、BCrypt
│   ├── models/models.go            # 中文标签 / badge / 日期 / 视图 / API 输出映射
│   ├── models/store.go             # 全部业务 SQL（? 占位符）
│   └── server/
│       ├── auth.go                 # 会话 Cookie、CSRF、HMAC 工具
│       ├── render.go               # 每页独立 template set、FuncMap、渲染
│       ├── handlers.go             # 页面处理与表单校验
│       ├── api.go                  # REST API 与 Token
│       └── router.go               # 路由与中间件
├── .env.example   .gitignore   start.sh   README.md
```

关键设计：

- **通用查询**：`database.Rows(sql, args...)` 用 `rows.Columns()` 动态扫描，统一把 `[]byte → string`、
  `time.Time → "2006-01-02 15:04:05"`、`nil → nil`，所有查询复用它。
- **模板隔离**：`base.html` 定义 `{{define "base.html"}}` 布局与 `{{define "taskCards"}}` 片段，
  每个页面只定义 `{{define "content"}}`；渲染时按页构造独立 template set 避免 define 冲突。
  FuncMap 提供 `has(ids,id)`、`eq64(a,b)`、`safe(s)`。
- **路由说明**：gin 路由树不允许同一层既有静态段又有通配段，故 `/projects/new/` 合并进
  `/projects/:id/` 内部分流（`id == "new"`），对外行为与独立路由完全一致。
- **描述换行**：先 `html.EscapeString`，再把换行替换为 `<br>`，经 `template.HTML` 安全输出。
- **看板排序**：`FIELD(status,'todo','doing','done'), FIELD(priority,'high','medium','low'),
  due_date IS NULL, due_date, id`。

---

## 8. 注意事项与已知限制

- 任务的**新建 / 编辑 / 删除目前仅要求登录**，未按项目负责人或执行人做对象级权限校验（与 Django 版一致）；
  项目的编辑与删除已限制为负责人。
- API 的项目写操作（PUT / DELETE）同样仅负责人；用户管理仅 `is_staff` 管理员。
- Bootstrap 5 的 CSS / JS 来自 jsDelivr CDN，离线或内网部署需改为本地静态资源。
- `SESSION_SECRET` / `API_SECRET` 为开发占位值，**生产环境必须替换**，并开启 `COOKIE_SECURE=true`。
- 项目列表暂未分页；工作台「我的项目」「我的待办」分别只展示前 5 / 10 条。
- 构建机（本机）**未安装 Go 工具链**，且 `proxy.golang.org` 超时，故仓库不含 `go.sum`、
  代码未经 `go build` 编译验证；请在具备 Go 1.22+ 与网络的环境执行
  `export GOPROXY=https://goproxy.cn,direct && go mod tidy && go run .`。
