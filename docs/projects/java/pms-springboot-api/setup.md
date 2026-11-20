# Spring Boot REST API 搭建过程详解

本文说明如何运行独立的 Spring Boot REST API，并让共享 React 前端通过它读写个人效能数据。该服务不渲染业务页面；服务端页面版见 `../pms-springboot/`。

## 1. 环境准备

- JDK 17 或更高版本。
- Maven 3.8 或更高版本。
- MySQL 8.0 或更高版本。
- Node.js 与 npm（本地开发 React 时需要）。

启动 MySQL，并创建共享数据库：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

API 启动时会执行 `src/main/resources/schema.sql` 建表；数据库本身应预先存在。

## 2. 配置并启动 API

```bash
cd versions/java/pms-springboot-api
cp .env.example .env
```

编辑 `.env` 中的 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`。`API_SECRET` 用于签发和校验 HMAC Token；若与 Gin API 或 FastAPI 共用 Token，三者密钥必须一致。生产环境要替换开发密钥，并设置强管理员密码。

此项目的 `./start.sh` 会加载 `.env` 并运行 Maven：

```bash
./start.sh
```

默认端口为 `8080`，可用 `SERVER_PORT` 修改。直接运行 `mvn spring-boot:run` 或 JAR 时，Spring Boot 不会自动读取 `.env`，需要自行设置环境变量。

## 3. 连接共享 React

另开终端，从仓库根目录进入 React 项目并启动开发服务器：

```bash
cd versions/react/pms-react
npm ci
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
```

Vite 将 `/api` 和 `/health` 代理到 Spring API。浏览器打开 Vite 显示的地址，React 使用 Token 调用 API。若前后端分开部署，应配置 API 的 `CORS_ORIGINS`，并按部署方式设置 React 的 `VITE_API_BASE_URL`。

## 4. 验证和构建

在 API 项目目录执行：

```bash
mvn test
mvn package
```

运行 API 后可按 API 契约 验证登录和业务端点。React 的单测、类型检查与构建：

```bash
cd versions/react/pms-react
npm test
npm run typecheck
npm run build
```

## 5. 常见问题

- **应用启动时报数据库错误**：确认数据库已创建，MySQL 正常运行，DB_* 配置及账号权限正确。
- **Token 在不同 API 版本间无效**：确认 Spring、Gin、FastAPI 的 `API_SECRET` 相同。
- **React 出现跨域错误**：本地开发使用 `VITE_API_PROXY_TARGET`；独立部署时检查 API 的 CORS 来源配置。
- **8080 端口已被占用**：修改 `SERVER_PORT`，并将 React 代理目标更新为新端口。
- **`.env` 修改后仍使用旧值**：检查系统环境变量是否覆盖 `.env`；重启 API 进程。

## 6. 关联资料

- REST API 契约：API 契约
- 项目概述：[README.md](../../../../versions/java/pms-springboot-api/README.md)
- 页面单体版：Spring Boot 单体页面版搭建详解
- React 前端：React 搭建详解
