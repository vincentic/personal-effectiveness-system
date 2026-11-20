# PMS Flask 单体版

Flask 单体版提供服务端渲染的工作台与日 / 周 / 月清单页面，使用独立 Session 登录和 CSRF 保护。它与 Django、FastAPI、Gin 和 Spring Boot 共享 MySQL v2 数据模型及 BCrypt 账号密码格式，但不提供 REST API；共享 Vue 应连接独立的 API 服务。

## 快速开始

要求 Python 3.10+ 和 MySQL 8.0+。创建共享数据库后启动：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cd versions/python/pms-flask
cp .env.example .env
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./start.sh
```

应用首次请求时会幂等初始化 schema；只有 `users` 表为空时才创建 `.env` 中的默认管理员。开发页面地址为 <http://127.0.0.1:8001/>。生产环境请设置高熵 `SECRET_KEY`、强管理员密码，并启用 HTTPS 与安全 Cookie。

完整搭建、构建和部署说明见[搭建指南](../../../docs/projects/python/pms-flask/setup.md)、[构建过程讲解](../../../docs/projects/python/pms-flask/build-process-explained.md)和[部署指南](../../../docs/projects/python/pms-flask/deployment.md)。

## 功能

- Session 登录、管理员用户创建、CSRF 防护和用户数据隔离。
- 工作台展示日 / 周 / 月记录数量、当前周期状态和近 7 日时间盒完成度。
- 每日执行清单：MIT、睡眠与精力、执行检查、生活维护、晚间复盘和可增删时间盒。
- 每周复盘清单：下周 MIT、复盘问题、四领域事实与可增删任务流。
- 每月重构清单：成果价值、系统调整、方向校准和六模块健康自评。
- 响应式 PC / Mobile 页面与可手动选择的视图模式。

## 配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `127.0.0.1:3306` | MySQL 地址 |
| `DB_NAME` | `pms` | 共享数据库 |
| `DB_USER` / `DB_PASSWORD` | `root` / 空 | 数据库账号 |
| `SERVER_PORT` | `8001` | Flask 开发服务端口 |
| `SECRET_KEY` | 开发占位值 | 生产必须替换为随机密钥 |
| `SESSION_COOKIE_SECURE` | `false` | HTTPS 生产环境设为 `true` |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | 仅在 users 表为空时初始化管理员 |

测试：

```bash
pip install -r requirements-dev.txt
pytest
```
