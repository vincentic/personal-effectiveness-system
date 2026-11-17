# 个人效能系统 · 多版本 REST API 契约（v2）

Spring Boot 版、Gin 版、FastAPI 版共用同一套接口与认证方式，也共用 `db/schema.sql` 的
v2 效能表（`users` + `daily_plans`/`time_blocks` + `weekly_reviews`/`weekly_domain_reviews`/`weekly_task_flows`
+ `monthly_reviews`/`system_health_checks`）与 BCrypt 密码，因此**同一个库、同一套账号、
同一个 Token 可在三个版本间互通**。FastAPI 版是纯 API 实现；另两版在服务端页面之外额外提供 `/api/**`。

所有数据均**按当前用户隔离**：只读写 `user_id = 当前用户` 的记录；访问他人或不存在的记录一律 `404`。
明细子表（时间盒 / 领域 / 任务流 / 健康检查）随主表在**同一次请求内整体替换**：请求体带 `blocks` /
`domains` / `flows` / `health_checks` 数组，服务端先删旧明细再按数组插入。

## 1. 认证（与 v1 相同，保持不变）

### Token 格式（自实现 HMAC-SHA256，无第三方 JWT 依赖）

```
claims  = {"uid": 1, "username": "admin", "staff": true, "exp": 1799999999}
payload = base64url(JSON(claims))            // 不带填充；JSON 紧凑序列化（分隔符 , 与 :）
sig     = hex(HMAC_SHA256(API_SECRET, payload))   // 对 payload 字符串本身签名
token   = payload + "." + sig
```

- `API_SECRET` 默认 `dev-insecure-api-secret-change-me`，生产必须替换；三个版本配置相同即可互认 Token。
- `exp` 为 Unix 秒，默认签发 24 小时；校验时用常量时间比较（`hmac.compare_digest` /
  `subtle.ConstantTimeCompare` / `MessageDigest.isEqual`），并检查 `exp > now`。
- 校验时**不要重新序列化 claims**，直接对收到的 payload 串验签。
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
| 400 | 字段校验失败 / JSON 非法 / 枚举越界 / 日期格式错 | `{"detail":"指标格式应为 YYYY-MM"}` |
| 401 | Token 缺失、签名错误、过期；登录密码错误 | `{"detail":"..."}` |
| 403 | 非管理员操作用户 | `{"detail":"仅管理员可操作"}` |
| 404 | 清单记录不属于当前用户或不存在 | `{"detail":"记录不存在"}` |
| 201 | 创建成功 | 新建对象 |
| 204 | 删除成功 | 空 |

## 3. 数据字段（snake_case，与 schema 列名一一对应）

**daily（每日执行清单）写入** `POST /api/daily` / `PUT /api/daily/{id}`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `plan_date` | string | 必填，`YYYY-MM-DD` |
| `sleep_hours` | number/null | 睡眠小时 |
| `energy_level` | enum | `low|medium|high`，默认 `medium` |
| `mit` | string | 今日最重要成果 |
| `pre_know_output`,`pre_materials_ready`,`pre_phone_handled` | bool | 启动前 3 项 |
| `during_one_block`,`during_shrink_step`,`during_record_distraction` | bool | 执行中 3 项 |
| `post_visible_output`,`post_recall`,`post_next_step` | bool | 结束后 3 项 |
| `life_water_food`,`life_walk_exercise`,`life_clean_tidy`,`life_social_outdoor` | bool | 生活维护 4 项 |
| `review_accomplished`,`review_resistance`,`review_tomorrow_start`,`review_gratitude` | string | 晚间复盘 4 问 |
| `blocks` | array | 时间盒组块明细，见下 |

**time_block**（`blocks[]` 元素）：`seq`(int)、`block_name`(string，必填)、`input_desc`、`key_action`、
`deliverable`(string)、`time_start`/`time_end`(`HH:MM` 或 null)、`is_done`(bool)。

**daily 返回**：上述全部字段 + `id`、`user_id`、`created_at`、`blocks[]`、
`blocks_total`、`blocks_done`、`block_progress`(0–100)、`checklist_total`(13)、`checklist_done`(0–13)。

**weekly（每周复盘清单）写入** `POST /api/weekly`：

- `week_start`（必填，`YYYY-MM-DD`，建议周一）、`next_mit_1/2/3`、`deep_work_time`、
  `prep_env_materials`、`if_condition`、`then_action`
- 复盘 5 问布尔：`q_skilled_block`、`q_low_value_time`、`q_estimate_gap`、`q_rest_connection`、`q_next_adjust`
- `domains[]`：`{domain: work|study|body|relations, facts, status: smooth|blocked|paused}`（最多 4 条，domain 不重复）
- `flows[]`：`{bucket: todo|doing|done|remove, content, seq}`

**weekly 返回**：上述字段 + `id`、`user_id`、`week_end`(读时计算 = week_start+6天)、`created_at`、
`domains[]`、`flows[]`。

**monthly（每月重构清单）写入** `POST /api/monthly`：

- `month`（必填，`YYYY-MM`）、`top_achievement`、`worth_continue`(`yes|adjust|stop`)、`worth_reason`、
  `most_valuable_input`、`biggest_opportunity_cost`、`keep_item`、`cut_item`、`experiment_item`
- 季度校准 5 问布尔：`q_skill_portfolio`、`q_career_alignment`、`q_life_autonomy`、
  `q_recovery_relations`、`q_longterm_decision`
- `health_checks[]`：`{module: direction|taskload|blockquality|timebox|sleepenergy|reviewquality,
  score: 1-5 或 null, needed_change}`（最多 6 条，module 不重复）

**monthly 返回**：上述字段 + `id`、`user_id`、`created_at`、`health_checks[]`、`avg_score`(1–5 或 null)。

**user 返回**：`id`、`username`、`display_name`、`is_staff`、`created_at`（**绝不包含密码哈希**）。

## 4. 端点

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 公开 | `{username,password}` → `{token, user}` |
| GET | `/api/me` | Bearer | 当前用户 |
| GET | `/api/dashboard` | Bearer | 效能概览（见下） |
| GET | `/api/daily` | Bearer | 我的每日清单列表（按日期倒序） |
| POST | `/api/daily` | Bearer | 新建（含 blocks 明细） |
| GET | `/api/daily/{id}` | Bearer | 详情（含 blocks） |
| PUT | `/api/daily/{id}` | Bearer | 更新（含 blocks 整体替换） |
| DELETE | `/api/daily/{id}` | Bearer | 删除（级联 blocks） |
| GET | `/api/weekly` | Bearer | 我的每周复盘列表（按周倒序） |
| POST | `/api/weekly` | Bearer | 新建（含 domains/flows） |
| GET | `/api/weekly/{id}` | Bearer | 详情 |
| PUT | `/api/weekly/{id}` | Bearer | 更新（含 domains/flows 整体替换） |
| DELETE | `/api/weekly/{id}` | Bearer | 删除（级联 domains/flows） |
| GET | `/api/monthly` | Bearer | 我的每月重构列表（按月份倒序） |
| POST | `/api/monthly` | Bearer | 新建（含 health_checks） |
| GET | `/api/monthly/{id}` | Bearer | 详情 |
| PUT | `/api/monthly/{id}` | Bearer | 更新（含 health_checks 整体替换） |
| DELETE | `/api/monthly/{id}` | Bearer | 删除（级联 health_checks） |
| GET | `/api/users` | Bearer + 管理员 | 用户列表 |
| POST | `/api/users` | Bearer + 管理员 | 创建用户（密码 BCrypt） |

`GET /api/dashboard` 返回（按当前用户统计）：

```json
{
  "daily_count": 3, "weekly_count": 2, "monthly_count": 1,
  "today_plan": null, "week_review": null, "month_review": null,
  "recent_daily": [ /* daily */ ], "recent_weekly": [ /* weekly */ ],
  "recent_monthly": [ /* monthly */ ],
  "block_total": 8, "block_done": 5
}
```

`today_plan` / `week_review` / `month_review` 分别为「今天 / 本周（周一起）/ 本月（YYYY-MM）」
的清单对象或 null；`block_total`/`block_done` 为最近 7 日清单的时间盒汇总。

## 5. 权限边界

- 所有日/周/月清单仅本人可读写，跨用户访问返回 404（无 Django v1 的对象级权限缺陷）。
- 用户管理仅 `is_staff` 管理员。
- 删除主表由外键级联删除全部明细。

## 6. 快速验证

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s http://127.0.0.1:8000/api/dashboard -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://127.0.0.1:8000/api/daily -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"plan_date":"2026-11-17","energy_level":"high","mit":"完成迁移","blocks":[{"seq":1,"block_name":"改造三版","is_done":true}]}'
```

三个版本的默认端口：Spring Boot `8080`、Gin `8081`、FastAPI `8000`。
