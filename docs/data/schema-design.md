# 个人效能系统 · 表结构设计（v2）

本设计依据附件《个人效能系统 · 可手写打印 · 版本 1.0》的三张 A4 清单，把原 PMS（项目/任务）数据模型整体替换为效能模型。`db/schema.sql` 是 Gin、Spring Boot、FastAPI 与 Flask 两个项目共用的 v2 schema；Django 按相同业务实体使用自己的 ORM 迁移与表名。Gin、Spring Boot、FastAPI 和 Flask API 共用 REST API 契约。

## 一、PDF 清单 → 数据表映射

| PDF 页面 | 页面分区 | 对应表 | 说明 |
|---|---|---|---|
| 第 1 页 每日执行清单 | 今日方向（日期/睡眠/精力/MIT） | `daily_plans` | 一人一天唯一 |
| | 今日组块与时间盒（输入/关键动作/可验收输出/时段/完成） | `time_blocks` | 每块一行，归属日清单 |
| | 执行前后检查（启动前/执行中/结束后 各 3 项） | `daily_plans` 9 个布尔字段 | 一次性勾选 |
| | 生活维护（4 项） | `daily_plans` 4 个布尔字段 | 一次性勾选 |
| | 晚间 5 分钟复盘（4 问） | `daily_plans` 4 个文本字段 | 自由填写 |
| 第 2 页 每周复盘清单 | 本周回顾：4 领域事实 + 状态 | `weekly_domain_reviews` | 4 行（work/study/body/relations） |
| | 任务流清理（待办/进行中/已完成/删除委托延后） | `weekly_task_flows` | 每条一行，按 bucket 分栏 |
| | 下周设计（1–3 项成果/深度时间/环境材料/If-Then） | `weekly_reviews` | 一人一周唯一 |
| | 复盘问题（5 项） | `weekly_reviews` 5 个布尔字段 | 一次性勾选 |
| 第 3 页 每月重构清单 | 目标与价值检查 | `monthly_reviews` | 一人一月唯一 |
| | 系统健康检查（6 模块自评 1–5 分 + 改变） | `system_health_checks` | 6 行 |
| | 下月三项改进（保留/删减/实验） | `monthly_reviews` | 3 个文本字段 |
| | 季度方向校准（5 项） | `monthly_reviews` 5 个布尔字段 | 一次性勾选 |

## 二、表关系（ER 概览）

```
users ─┬─< daily_plans ──< time_blocks
       ├─< weekly_reviews ─┬─< weekly_domain_reviews
       │                   └─< weekly_task_flows
       └─< monthly_reviews ───< system_health_checks
```

- 所有子表 `ON DELETE CASCADE` 跟随主表删除。
- 三张主表各带唯一键：`(user_id, plan_date)` / `(user_id, week_start)` / `(user_id, month)`，保证一天/一周/一月一条。
- 子表唯一键防止重复分项：`(weekly_review_id, domain)` / `(monthly_review_id, module)`。

## 三、字段约定

- **枚举用短字符串 + 注释**：energy `low/medium/high`；domain `work/study/body/relations`；status `smooth/blocked/paused`；bucket `todo/doing/done/remove`；worth `yes/adjust/stop`；module `direction/taskload/blockquality/timebox/sleepenergy/reviewquality`。
- **布尔勾选**统一 `TINYINT NOT NULL DEFAULT 0`（MySQL）。
- **自评分** `TINYINT` 带 1–5 校验（Django 层 `MinValueValidator`/`MaxValueValidator`）。
- **月份** `CHAR(7)` 形如 `2026-11`；**日期** `DATE`；**时间盒** `TIME`。
- 所有表 `utf8mb4 / utf8mb4_unicode_ci`，建表语句幂等（`IF NOT EXISTS`）。

## 四、各版本状态

| 版本 | 数据模型 | 界面 | 说明 |
|---|---|---|---|
| Django | v2 效能模型（`projects/models.py` + `0001_initial.py`） | ✅ 日/周/月三张清单 | 参照实现，完整 CRUD，Session 页面 |
| Spring Boot | v2 效能模型（JdbcTemplate repo） | ✅ 日/周/月三张清单 | Security + CSRF + `/api/**` |
| Gin | v2 效能模型（database/sql store） | ✅ 日/周/月三张清单 | 签名 Cookie + CSRF + `/api/**` |
| FastAPI | v2 效能模型（PyMySQL） | Vue 3 响应式页面 + REST API | 同源提供页面、全部 `/api/**` + OpenAPI 文档 |

> `db/schema.v1-legacy.sql` 保存了 v1 的 `users / projects / project_members / tasks` 建表语句，仅供历史数据库归档使用；v3 所有版本统一用 `db/schema.sql`。

## 五、Django 参照实现要点

- `DailyPlan` 提供 `blocks_total` / `blocks_done` / `block_progress` / `checklist_done` 等读时计算属性，供工作台与列表展示进度。
- 表单用 `inlineformset_factory` 管理重复明细：日清单 4 个时间盒、周复盘 4 领域 + 8 任务流、月重构 6 项健康检查；新建时为领域/模块预填初始值。
- 所有明细读写经 `user=request.user` 过滤，跨用户访问返回 404。
- 界面沿用原 CSS 设计体系，在第 10 节追加清单专用样式（分区序号、柔和徽章、细进度条、勾选行等）。
