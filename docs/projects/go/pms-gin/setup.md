# Gin 单体页面版搭建过程详解

本文介绍如何在本地搭建 Go/Gin 服务端渲染版本。该项目独立运行，使用 Session 登录和 HTML 模板，不提供供 Vue 调用的 REST API；API 版本见 [`../pms-gin-api/`](../pms-gin-api/setup.md)。

## 1. 环境准备

- Go 1.22 或更高版本。
- MySQL 8.0 或更高版本，并确保服务已启动。
- Git（用于获取项目代码）。

从仓库根目录创建应用所用数据库：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

应用启动时会执行项目内嵌的 `sql/schema.sql` 幂等建表脚本；无需手动导入建表文件，但数据库本身必须预先存在。

## 2. 配置数据库和管理员

进入项目目录，复制配置模板：

```bash
cd versions/go/pms-gin
cp .env.example .env
```

编辑 `.env` 中的 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER` 和 `DB_PASSWORD`，使其与本地 MySQL 匹配。首次启动且 `users` 表为空时，`ADMIN_USERNAME` 和 `ADMIN_PASSWORD` 用于创建初始管理员；本地模板默认账号为 `admin / admin123`。

`SESSION_SECRET` 用于签署登录 Session Cookie。生产环境必须换成随机强密钥；启用 HTTPS 后将 `COOKIE_SECURE=true`。`.env` 不应提交到版本库。

## 3. 启动与登录

在项目目录运行：

```bash
go run .
```

默认监听 `8081` 端口。浏览器打开 <http://127.0.0.1:8081/login>，使用上述管理员账号登录。若端口被占用，可在 `.env` 中修改 `SERVER_PORT`。

## 4. 验证和开发

```bash
go test ./...
go build ./...
```

登录后可验证每日执行、每周复盘、每月重构，以及 PC / Mobile 页面切换。数据属于当前登录用户；页面写操作包含 CSRF 防护。

## 5. 常见问题

- **数据库连接失败**：确认 MySQL 正在运行、`pms` 数据库已创建，并检查 `.env` 的 DB_* 配置和账号权限。
- **配置修改未生效**：Go 程序在启动时读取项目目录中的 `.env`；修改后需停止并重新启动服务。已设置的系统环境变量优先于 `.env`。
- **8081 端口被占用**：修改 `SERVER_PORT`，并通过新的端口访问页面。
- **首次登录失败**：默认管理员只会在 `users` 表为空时创建；如果数据库已有用户，请使用现存账号登录。

## 6. 关联资料

- 项目概述与快速启动：[README.md](../../../../versions/go/pms-gin/README.md)
- 生产部署与 Nginx / systemd 配置：[部署指南](deployment.md)
- API 对应项目：[Gin API 搭建详解](../pms-gin-api/setup.md)
