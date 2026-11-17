# 个人效能系统（原 PMS 项目管理系统）· 四版本实现

> **v2（当前）**：依据《个人效能系统 · 版本 1.0》三张清单（每日执行 / 每周复盘 / 每月重构），数据模型整体替换为效能模型。**Django 版已改造为参照实现**（日/周/月三张清单界面，完整 CRUD）。其余三版本轮保留 v1 代码。
>
> **v1（历史基线）**：四版本 PMS（项目/任务/成员管理），共用 MySQL 表结构与 REST API 契约，账号/数据/Token 互通。提交 `a702875` 为 v1 基线，旧建表语句见 [db/schema.v1-legacy.sql](db/schema.v1-legacy.sql)。

## 版本与数据模型对照（v2）

| 版本 | 数据模型 | 界面 | 说明 |
|------|---------|------|------|
| Django | ✅ v2 效能模型 | ✅ 日/周/月三张清单 | 参照实现，已完整改造 |
| Spring Boot | v1（projects/tasks） | v1 旧界面 | 本轮未改；如需独立运行用 legacy schema |
| Gin | v1（projects/tasks） | v1 旧界面 | 本轮未改 |
| FastAPI | v1（projects/tasks） | 纯 API | 本轮未改 |

**效能系统三张清单 ↔ 数据表**：每日执行（`daily_plans` + `time_blocks`）、每周复盘（`weekly_reviews` + `weekly_domain_reviews` + `weekly_task_flows`）、每月重构（`monthly_reviews` + `system_health_checks`）。完整字段映射与设计说明见 [db/DESIGN.md](db/DESIGN.md)。

### Django 版快速体验（v2）

```bash
cd pms-django
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
# 按 .env 配置好 MySQL 后：
python manage.py migrate
python manage.py runserver 8000
# 登录后依次进入：每日执行 / 每周复盘 / 每月重构
```

> 迁移说明：Django 版的 `0001_initial.py` 已重建为 v2 效能模型。若数据库已有 v1 的 Django 表（auth_user / projects_project 等），需在全新库上使用，或自行编写数据迁移。

## 目录结构

```
pms/                      仓库根目录
├── pms-django/           Django 4.2 版（原项目，服务端渲染页面）
├── pms-springboot/       Spring Boot 3.2 版（Thymeleaf 页面 + REST API）
├── pms-gin/              Gin 版（Go 1.27，html/template 页面 + REST API）
├── pms-fastapi/          FastAPI 版（纯 REST API + OpenAPI 文档）
├── db/                   四版共享：schema.sql（建表）+ API.md（API 契约）
└── README.md             本文件
```

## 版本对照

| 版本 | 技术栈 | 默认端口 | 页面 | REST API | 会话方式 | 状态 |
|------|--------|----------|------|----------|----------|------|
| Django | Django 4.2 + MySQL | 8000 | ✅ 完整 | ❌ | Django Session | 原版 |
| Spring Boot | Spring Boot 3.2 + Thymeleaf + Spring Security + JdbcTemplate | 8080 | ✅ 完整 | ✅ `/api/**` | Spring Session + CSRF | 已编译并实测 |
| Gin | Gin 1.10 + database/sql + html/template | 8081 | ✅ 完整 | ✅ `/api/**` | 签名 Cookie + CSRF | 已编译并实测 |
| FastAPI | FastAPI + PyMySQL + BCrypt | 8000 | ❌ 纯 API | ✅ 全部 | HMAC Token | 已实测 |

> Django 与 FastAPI 默认都是 8000，同时启动时需用环境变量（如 `SERVER_PORT`）错开。

## 部署文档

每个版本都有独立的部署文档，涵盖环境准备、建库、配置、开发启动、生产部署（systemd + Nginx）与故障排查：

| 版本 | 部署文档 |
|------|---------|
| Django | [pms-django/部署文档.md](pms-django/部署文档.md)（另有 [热部署文档.md](pms-django/热部署文档.md)，不停机发布） |
| Spring Boot | [pms-springboot/部署文档.md](pms-springboot/部署文档.md) |
| Gin | [pms-gin/部署文档.md](pms-gin/部署文档.md) |
| FastAPI | [pms-fastapi/部署文档.md](pms-fastapi/部署文档.md) |

> 生产环境务必修改各版本 `.env` 中的数据库账号、管理员密码与密钥；三版 `API_SECRET` 需保持一致才能跨版本互认 Token。

## 共享数据层

- **建表脚本**：`db/schema.sql`（四份副本内容一致，分别位于各版本的 `sql/` 目录与 Spring Boot 的 `src/main/resources/`）。
- **表**：`users`、`projects`、`project_members`、`tasks`。
- **密码**：全部使用 BCrypt 哈希。
- **默认管理员**：`admin / admin123`（首次启动且 users 表为空时自动创建，生产环境务必用 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 覆盖）。
- **API 契约**：见 `db/API.md`。Token 格式为 `base64url(JSON claims) + "." + hex(HMAC-SHA256(API_SECRET, payload))`，三版互通。

## 快速开始

### 公共前提：MySQL

```bash
mysql -u root -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

若本机 `.env` 中已有 pms 库配置，四版会自动读取。

### Django 版

```bash
cd pms-django
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py runserver 8000
```

### Spring Boot 版

```bash
cd pms-springboot
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
mvn spring-boot:run                                # 默认 8080
```

### Gin 版

```bash
cd pms-gin
export GOPROXY=https://goproxy.cn,direct
go mod tidy && go run .                            # 默认 8081
```

### FastAPI 版

```bash
cd pms-fastapi
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000          # 文档：/docs
```

## API 互通验证

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# 同一个 Token 可访问另外两版
curl -s http://127.0.0.1:8081/api/me -H "Authorization: Bearer $TOKEN"
curl -s http://127.0.0.1:8000/api/me -H "Authorization: Bearer $TOKEN"
```

## 构建与实测状态

| 检查项 | 结果 |
|--------|------|
| Spring Boot `mvn test` | ✅ 通过 |
| Gin `go mod tidy && go build ./...`（Go 1.27.4 via gvm） | ✅ 通过 |
| FastAPI 登录 / CRUD / Token 互通 | ✅ 通过 |
| 三版 Token 交叉验证（3×3 = 9 组合） | ✅ 全部 200 |
| 跨版本 CRUD（FastAPI 建 → 三版读，Spring 改 → 三版统计） | ✅ 通过 |
| Django `manage.py check`（迁移到 pms-django 后） | ✅ 无问题 |

## 迁移说明

原 Django 项目的全部文件已移入 `pms-django/`，与其余三版同级。移动前已做完整备份：
`/Users/new/Documents/pms-backup-20261117-115251`。

Django 虚拟环境在新位置重建（路径已更新）；`热部署文档.md` 中的工作目录示例已同步改为 `pms/pms-django`。
