# Spring Boot 单体页面版搭建过程详解

本文说明如何本地搭建 Spring Boot + Thymeleaf 服务端页面版本。该项目提供 PC / Mobile 页面和 Session 登录，不是 REST API 服务；API 项目见 [`../pms-springboot-api/`](../pms-springboot-api/setup.md)。

## 1. 环境准备

- JDK 17 或更高版本。
- Maven 3.8 或更高版本。
- MySQL 8.0 或更高版本。

先启动 MySQL，并创建应用数据库：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Spring Boot 启动时会运行 `src/main/resources/schema.sql`；数据库需先存在。

## 2. 配置环境

在项目目录复制模板并编辑数据库连接：

```bash
cd versions/java/pms-springboot
cp .env.example .env
```

设置 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER` 和 `DB_PASSWORD`。`ADMIN_USERNAME` / `ADMIN_PASSWORD` 仅在 `users` 表为空时创建首个管理员。本地默认值为 `admin / admin123`，非本地环境必须替换。

注意：Spring Boot 不会自行读取 `.env`。使用项目内的 `./start.sh` 会加载 `.env` 再启动 Maven；若直接执行 `mvn spring-boot:run`，请先通过 shell 或运行环境设置所需环境变量。系统环境变量优先。

## 3. 启动并访问

```bash
./start.sh
```

默认服务端口为 `8080`。打开 <http://127.0.0.1:8080/login> 并登录。页面版使用 Spring Security Session 和 CSRF 保护，不提供共享 Vue 使用的 `/api/**` 接口。

## 4. 验证与打包

```bash
mvn test
mvn package
```

打包后可用以下命令启动（启动环境需注入数据库等配置）：

```bash
java -jar target/pms-springboot-1.0.0.jar
```

## 5. 常见问题

- **`.env` 配置看似未生效**：确认使用 `./start.sh`；直接使用 Maven 或 JAR 时要自行注入环境变量。
- **数据库连接失败**：检查 MySQL 服务、数据库名、账号权限和 DB_* 配置。
- **管理员登录失败**：初始管理员只在用户表为空时创建，之后需使用已存在的账号。
- **8080 端口冲突**：在 `.env` 中修改 `SERVER_PORT` 并重启。
- **希望连接 Vue**：请使用 Spring Boot API 项目，而不是此服务端页面版。

## 6. 关联资料

- 项目说明：[README.md](../../../../versions/java/pms-springboot/README.md)
- 生产部署：[部署指南](deployment.md)
- 更完整的需求、架构和实现讲解：[搭建过程讲解.md](build-process-explained.md)
- REST API 对应项目：[Spring Boot API 搭建详解](../pms-springboot-api/setup.md)
