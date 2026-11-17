# PMS 多版本 REST API 契约

Spring Boot 版、Gin 版、FastAPI 版共用同一套接口与认证方式，也共用 `db/schema.sql` 的
`users / projects / project_members / tasks` 四张表和 BCrypt 密码，因此**同一个库、同一套账号、
同一个 Token 可在三个版本间互通**。FastAPI 版是纯 API 实现；另两版在服务端页面之外额外提供 `/api/**`。

## 1. 认证

### Token 格式（自实现 HMAC-SHA256，无第三方 JWT 依赖）

```
claims  = {"uid": 1, "username": "admin", "staff": true, "exp": 1799999999}
payload = base64url(JSON(claims))            // 不带填充；JSON 紧凑序列化（分隔符 , 与 :）
sig     = hex(HMAC_SHA256(API_SECRET, payload))   // 对 payload 字符串本身签名
token   = payload + "." + sig
```

- `API_SECRET` 默认 `dev-insecure-api-secret-change-me`，生产必须替换；三个版本配置相同即可互认 Token。
- `exp` 为 Unix 秒，默认签发 24 小时；校验时用 `subtle.ConstantTimeCompare` / `hmac.compare_digest` /
  `MessageDigest.isEqual` 做常量时间比较，并检查 `exp > now`。
- 校验时**不要重新序列化 claims**，直接对收到的 payload 串验签，因此 JSON 字段顺序无关紧要。
- 页面登录仍走各自原有的 Session / 签名 Cookie，与 API Token 相互独立。

### 请求头

```
Authorization: Bearer <token>
Content-Type: application/json
```

除 `/api/auth/login` 外，所有 `/api/**` 均需 Token；缺失或无效返回 `401 {"detail":"未认证或 Token 已过期"}`。
API 路由豁免 CSRF（页面表单仍保留 CSRF 保护）。

## 2. 统一错误

| 状态码 | 场景 | 响应体 |
|--------|------|--------|
| 400 | 字段校验失败 / JSON 非法 | `{"detail":"项目名称不能为空"}` |
| 401 | Token 缺失、签名错误、过期；登录密码错误 | `{"detail":"..."}` |
| 403 | 非负责人修改 / 删除项目；非管理员操作用户 | `{"detail":"仅负责人可操作"}` |
| 404 | 项目 / 任务不存在 | `{"detail":"项目不存在"}` |
| 201 | 创建成功 | 新建对象 |
| 204 | 删除成功 | 空 |

## 3. 数据字段

**project 写入**：`name`（必填，≤100）、`description`、`status`（`active|paused|done`）、
`start_date` / `end_date`（`YYYY-MM-DD` 或 null）、`member_ids`（int 数组）。

**project 返回**：上述字段 + `id`、`owner_id`、`owner_name`、`member_ids`、
`task_count`、`done_task_count`、`progress`（0-100，读时计算）、`created_at`。

**task 写入**：`title`（必填，≤200）、`description`、`assignee_id`（int 或 null）、
`status`（`todo|doing|done`）、`priority`（`low|medium|high`）、`due_date`（`YYYY-MM-DD` 或 null）。

**task 返回**：上述字段 + `id`、`project_id`、`assignee_name`、`created_at`。

**user 返回**：`id`、`username`、`display_name`、`is_staff`、`created_at`（**绝不包含密码哈希**）。

## 4. 端点

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 公开 | `{username,password}` → `{token, user}` |
| GET | `/api/me` | Bearer | 当前用户 |
| GET | `/api/dashboard` | Bearer | 全局统计 + 我的项目（5）+ 我的待办（10） |
| GET | `/api/projects` | Bearer | 列表，支持 `?status=` 与 `?q=` |
| POST | `/api/projects` | Bearer | 创建（创建者自动为负责人） |
| GET | `/api/projects/{id}` | Bearer | 详情 + `tasks` + `member_ids` |
| PUT | `/api/projects/{id}` | Bearer + 负责人 | 更新 |
| DELETE | `/api/projects/{id}` | Bearer + 负责人 | 删除（级联任务） |
| POST | `/api/projects/{id}/tasks` | Bearer | 新建任务 |
| PUT | `/api/tasks/{id}` | Bearer | 更新任务 |
| DELETE | `/api/tasks/{id}` | Bearer | 删除任务 |
| GET | `/api/users` | Bearer + 管理员 | 用户列表 |
| POST | `/api/users` | Bearer + 管理员 | 创建用户（密码 BCrypt） |

`GET /api/dashboard` 返回：

```json
{
  "project_count": 3, "task_count": 12,
  "todo_count": 5, "doing_count": 4, "done_count": 3,
  "my_projects": [ /* project */ ],
  "my_tasks": [ /* task */ ]
}
```

## 5. 权限边界（与 Django 版一致）

- 项目**编辑 / 删除**仅负责人；任务写操作仅要求登录（原版已知限制）。
- 用户管理仅 `is_staff` 管理员。
- 项目删除级联删除任务；删除执行人时任务 `assignee_id` 置空（由外键保证）。

## 6. 快速验证

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s http://127.0.0.1:8000/api/dashboard -H "Authorization: Bearer $TOKEN"
```

三个版本的默认端口：Spring Boot `8080`、Gin `8081`、FastAPI `8000`。
