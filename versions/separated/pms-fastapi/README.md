# 个人效能系统 · FastAPI 版

FastAPI 版将 Vue 3 响应式页面与 REST API 部署在同一服务中。它适合希望用网页管理个人日程和复盘，同时也需要标准 API 供脚本或其他客户端调用的场景。系统与 Django、Spring Boot、Gin 版共用 v2 数据模型和账号数据，并与 Spring Boot / Gin 共用 REST API 与 Token 契约；数据按当前用户隔离。

## 业务场景

系统围绕「日执行 → 周复盘 → 月重构」建立个人效能闭环：

| 节奏 | 常见问题 | 使用方式 |
|---|---|---|
| 每日执行 | 忙碌一天却没有明确成果，注意力容易被打断 | 设定一个 MIT（最重要成果），拆成有起止时间、关键动作和可验收输出的时间盒；晚间记录成果、阻力与明日启动动作 |
| 每周复盘 | 待办不断累积，计划过满，重复遇到相同阻力 | 按工作、学习、身体、关系回顾事实；将任务归入待办、进行中、已完成或删除/委托/延后；为下周选出 1–3 项成果 |
| 每月重构 | 目标可能已经改变，工具和流程逐渐变成负担 | 检查目标价值与系统健康，选择保留、删减和实验的改进，必要时调整长期方向 |

适用人群包括知识工作者、研发和产品人员、自由职业者、远程工作者、学生与研究者，以及希望建立个人工作节奏的管理者。系统重点解决成果不清晰、任务只进不出、计划缺乏余量、没有复盘反馈和忽略休息恢复等问题。

## 技术架构

```text
浏览器（PC / 手机）
  └─ Vue 3 + TypeScript 单页应用
       ├─ Vite 开发服务器：代理 /api、/health 到 FastAPI
       └─ 生产构建：静态资源由 FastAPI 同源提供
                         │ HTTP / JSON + Bearer Token
                         ▼
                  FastAPI 应用
                  ├─ 页面与静态资源：/、/assets/*
                  ├─ REST API：/api/auth、/api/dashboard、/api/daily、/api/weekly、/api/monthly
                  ├─ Pydantic 请求校验与用户数据隔离
                  └─ PyMySQL 数据访问
                         │
                         ▼
                  MySQL（共享 v2 数据模型）
```

- **前端**：Vue 3、TypeScript、Vite；PC / 手机使用同一响应式页面。构建结果输出至 `app/web`。
- **后端**：Python 3.10+、FastAPI、Pydantic、Uvicorn；启动时幂等初始化数据库表。
- **持久化与认证**：MySQL 8、PyMySQL；密码使用 BCrypt，API 使用 HMAC-SHA256 Token（有效期 24 小时）。
- **共享契约**：日、周、月记录与其他实现共享表结构，API 字段见 [`../../../db/API.md`](../../../db/API.md)，数据关系见 [`../../../db/DESIGN.md`](../../../db/DESIGN.md)。

## 使用指南

完整的日 / 周 / 月使用流程见[简体中文使用指南](../../../docs/user_guide.md)：[English](../../../docs/user_guide.en.md) · [繁體中文](../../../docs/user_guide.zh-TW.md) · [日本語](../../../docs/user_guide.ja.md)。

## 启动

确保 MySQL 已启动并创建数据库；首次使用时可先复制配置文件并按实际环境修改：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
test -f .env || cp .env.example .env
```

`ADMIN_USERNAME` / `ADMIN_PASSWORD` 仅在 users 表为空时用于创建首个管理员。首次本地启动默认值为 `admin` / `admin123`，请在非本地环境设置强密码。

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
npm --prefix frontend install
npm --prefix frontend run build
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```

应用页面：<http://127.0.0.1:8000/>（Vue 3，由 FastAPI 同源提供）
交互式 API 文档：<http://127.0.0.1:8000/docs>

浏览器登录后可在工作台与每日执行、每周复盘、每月重构页面间切换；手机浏览器使用折叠导航和响应式布局。通过 API 调用时，先请求 `POST /api/auth/login` 获取 Token，再在后续请求中携带 `Authorization: Bearer <token>`。

前端独立开发时，在一个终端启动 FastAPI（8000 端口），另一个终端运行：

```bash
cd frontend
npm install
npm run dev
```

访问 Vite 显示的地址；`/api` 与 `/health` 会代理到 FastAPI。部署前执行 `npm run build`，生成的 `app/web` 由 FastAPI 提供。未构建前端时访问 `/` 会返回明确的 503 构建提示，API 文档仍可访问。

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

见 `../../../db/API.md`。端点包括：

```
POST /api/auth/login
GET  /api/me
GET  /api/dashboard
GET|POST          /api/daily          # 每日执行清单（含 blocks 时间盒）
GET|PUT|DELETE    /api/daily/{id}
GET|POST          /api/weekly         # 每周复盘（含 domains 领域 + flows 任务流）
GET|PUT|DELETE    /api/weekly/{id}
GET|POST          /api/monthly        # 每月重构（含 health_checks 系统健康）
GET|PUT|DELETE    /api/monthly/{id}
GET|POST          /api/users          # 仅管理员
```

## 测试记录

- 登录返回 Token：✅
- 日/周/月清单 CRUD + 明细整体替换（真实 MySQL）：✅
- dashboard / 401 鉴权：✅
- 三版 Token 交叉验证与跨版本 CRUD：见仓库根 README 的联调结果。
