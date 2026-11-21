# Spring Boot REST API 详细搭建指南

本文从本地环境检查开始，逐步启动 MySQL、Spring Boot REST API 和共享 Vue 前端，并说明如何验证接口及排查常见错误。适合首次搭建或需要复现开发环境时使用。

快速步骤见[搭建指南](setup.md)，Maven 编译与打包见[构建过程讲解](build-process-explained.md)，API 字段和行为见[共享 API 契约](../../../data/api-contract.md)。

> 本文命令以 macOS/Linux 的 Bash/Zsh 为例。开发服务器默认使用 Spring API `8080`、Vue Vite `5173` 和 MySQL `3306`。

## 1. 了解项目结构与运行关系

本项目由两个独立进程组成：

```text
浏览器
  -> Vue/Vite :5173
  -> Vite 将 /api 请求代理到 Spring Boot :8080
  -> Spring Boot 通过 JDBC 连接 MySQL :3306
```

Spring Boot API 不负责渲染 Vue 页面，也不直接托管 Vue 开发服务器。开发模式下，Vue Vite 代理 `/api` 请求到后端，因此浏览器访问 Vue 时通常无需单独处理 CORS。

相关目录：

| 路径 | 用途 |
|---|---|
| `versions/java/pms-springboot-api/` | Spring Boot REST API |
| `versions/java/pms-springboot-api/src/main/resources/application.yml` | 端口、数据源、SQL 初始化等配置 |
| `versions/java/pms-springboot-api/src/main/resources/schema.sql` | 数据库建表脚本 |
| `versions/java/pms-springboot-api/.env.example` | 本地环境变量模板 |
| `versions/vue/pms-vue/` | 共享 Vue 前端 |
| `versions/vue/pms-vue/vite.config.ts` | Vite 开发代理及前端构建输出配置 |

## 2. 检查运行环境

需要准备：

- JDK 17 或更高版本。
- Maven 3.8 或更高版本。
- MySQL 8.0 或更高版本。
- Node.js 和 npm。

从仓库根目录执行：

```bash
cd /path/to/personal-effectiveness-system
java -version
mvn -version
node --version
npm --version
```

检查 `mvn -version` 中显示的 Java 版本也不低于 17。机器上可能安装多个 JDK；`java -version` 和 Maven 实际使用的 JDK 不一定相同。

macOS 使用 Homebrew 安装 MySQL 时，可检查服务状态：

```bash
brew services list
```

若已安装但尚未运行：

```bash
brew services start mysql
```

确认服务可连接：

```bash
mysqladmin ping -h 127.0.0.1 -uroot
```

若 root 账号需要密码，按提示输入；若本机配置允许 root 无密码连接，则命令不需要 `-p`。

## 3. 创建数据库

本 API 启动时会执行 `schema.sql` 创建表，但不会创建 MySQL 数据库本身。因此先创建数据库：

```bash
mysql -h 127.0.0.1 -u root -p \
  -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

按提示输入 MySQL root 密码。验证数据库存在：

```bash
mysql -h 127.0.0.1 -u root -p -e "SHOW DATABASES LIKE 'pms';"
```

### 可选：创建本地应用账号

也可以创建专供本应用使用的账号，而不是使用 root。在 MySQL 中执行并替换示例密码：

```sql
CREATE USER IF NOT EXISTS 'pms_app'@'127.0.0.1'
  IDENTIFIED BY 'replace-with-a-local-password';
GRANT ALL PRIVILEGES ON pms.* TO 'pms_app'@'127.0.0.1';
```

该账号需要在 `pms` 数据库内建表并读写数据。实际部署时应使用专门的最小权限账号和安全密码；不要把生产密码写入文档、源码或版本控制。

## 4. 配置 Spring Boot API

进入 API 项目目录，并在没有本地配置时复制模板：

```bash
cd /path/to/personal-effectiveness-system/versions/java/pms-springboot-api
if [ ! -f .env ]; then cp .env.example .env; fi
```

用编辑器打开 `.env`，例如：

```bash
open -e .env
```

按本机 MySQL 配置修改：

```dotenv
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=pms
DB_USER=root
DB_PASSWORD=

SERVER_PORT=8080
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin123
API_SECRET=dev-insecure-api-secret-change-me
CORS_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

说明：

- 如果使用前面创建的 `pms_app`，将 `DB_USER` 改为 `pms_app`，并将 `DB_PASSWORD` 设置为对应密码。
- MySQL root 有密码时，把密码写入 `DB_PASSWORD`；该项目的 `start.sh` 会将 `.env` 作为 Bash 配置加载，特殊字符值应使用适当引号。
- `ADMIN_USERNAME` 和 `ADMIN_PASSWORD` 只在 `users` 表为空时创建初始管理员。数据库已经有用户时，修改这两个变量不会重置已有账号密码。
- `API_SECRET` 用于 HMAC Token 签名。生产环境必须换成强随机密钥；若需要与 Gin/FastAPI 版本互认 Token，各 API 的密钥必须一致。
- `.env` 已在 API 项目的 `.gitignore` 中忽略，不应提交真实凭据。
- 这些值也可能被启动进程继承的系统环境变量覆盖；若配置似乎未生效，检查终端中是否已有同名变量并重启应用。

## 5. 启动 Spring Boot API

在 API 项目目录运行：

```bash
./start.sh
```

`start.sh` 会加载当前目录中的 `.env`，在 macOS 上尽可能选择 JDK 17，然后执行 `mvn spring-boot:run`。首次启动可能需要 Maven 下载依赖，请等待下载及应用初始化完成。

正常启动时，日志应包含类似信息：

```text
Tomcat started on port 8080
Started PmsApplication
```

保持该终端运行。启动时 Spring Boot 会连接 `.env` 指定的数据库，并执行 classpath 中的 `schema.sql`。默认 `continue-on-error: false`，SQL 初始化失败会阻止应用正常启动。

### 验证 API

可以在另一个终端先检查受保护接口的 HTTP 响应：

```bash
curl -i http://127.0.0.1:8080/api/me
```

未提供 Token 时预期返回 `401`。这只说明 HTTP 服务和路由正在响应；它本身不能证明数据库连接成功。

再验证登录。若使用默认本地配置，命令如下：

```bash
curl -i http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
```

预期登录成功并返回 HTTP `200`、`token` 和 `user`。不要将响应中的 Token 发布到工单、聊天记录或公开日志。若设置了自定义管理员账号密码，请相应修改请求 JSON。

## 6. 启动 Vue 前端

另开一个终端，进入 Vue 项目：

```bash
cd /path/to/personal-effectiveness-system/versions/vue/pms-vue
```

第一次安装依赖，或依赖锁文件发生变化后，运行：

```bash
npm ci
```

然后将 Vite 代理目标明确设置为 Spring API 的 `8080` 端口，再启动开发服务器：

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
```

必须设置这个变量：Vite 当前默认代理目标是 `http://127.0.0.1:8000`，这是其他后端运行模式的默认端口，不是本 Spring API 的 `8080`。

启动后终端会显示本地地址，通常是：

```text
http://localhost:5173/
```

在浏览器打开该地址，使用配置的管理员账号登录。Vite 会将 `/api` 请求代理到 Spring Boot。开发服务器运行期间不要关闭两个服务终端。

> 本地开发使用 Vite 代理时，通常不需要额外修改 CORS。若 Vue 和 API 生产环境分属不同站点，则应把 Vue 的实际 origin 加入 API 的 `CORS_ORIGINS`，并配置 Vue 的 `VITE_API_BASE_URL`。

## 7. 本地验证和停止

### Java 后端测试

在 API 项目目录运行：

```bash
mvn -B test
```

构建可执行 JAR：

```bash
mvn -B clean package
```

构建产物在 `target/pms-springboot-api-1.0.0.jar`。开发时建议使用 `./start.sh`；直接运行 JAR 不会自动读取 `.env`，必须由运行环境注入变量。

### Vue 验证

在 Vue 项目目录运行：

```bash
npm test
npm run typecheck
```

谨慎运行 `npm run build`：当前 Vite 配置的构建输出目录是 `versions/python/pms-fastapi/app/web`，可能替换 FastAPI 使用的静态前端文件。只有确认需要更新该构建产物时才运行。

### 停止服务

在 Spring Boot 和 Vue 各自运行的终端按 `Ctrl+C`。使用 Homebrew 管理的 MySQL 可继续运行；如需停止：

```bash
brew services stop mysql
```

## 8. 端口冲突处理

默认端口：

| 服务 | 默认端口 |
|---|---:|
| MySQL | 3306 |
| Spring Boot API | 8080 |
| Vue/Vite | 5173 |

如果 `8080` 被占用，在 API `.env` 修改：

```dotenv
SERVER_PORT=8082
```

然后重启 API，并用新端口设置 Vue 代理：

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8082 npm run dev
```

如果 Vite 的 `5173` 被占用，它可能会自动选择另一个端口；使用 Vite 启动日志显示的地址打开页面。若跨域部署，则也要将实际前端 origin 添加到 `CORS_ORIGINS`。

## 9. 常见问题排查

### `Failed to execute database script`

继续查看异常最深处的 `Caused by:`：

- `Communications link failure`：MySQL 未启动、host/port 错误或网络不可达。
- `Unknown database 'pms'`：先按第 3 节创建 `pms` 数据库。
- `Access denied for user`：核对 `DB_USER`、`DB_PASSWORD` 和 MySQL 用户授权。
- SQL 语法或存储引擎错误：确认连接的是 MySQL 8.0+，并查看具体报错对应的 `schema.sql` 语句。

`UnsatisfiedDependencyException` 可能只是上层 Bean 创建失败的包装异常；若其中提到 `dailyRepository`，也应沿异常链继续看数据库初始化失败的根因。

### Vue 页面可打开但登录失败

1. 确认 Spring Boot 日志显示应用启动成功。
2. 确认 Vue 使用 `VITE_API_PROXY_TARGET=http://127.0.0.1:8080` 启动，而不是默认的 `8000`。
3. 确认 `.env` 的 DB_* 连接信息正确。
4. 确认初始管理员确实是在空 `users` 表时创建；如果表已有用户，默认密码不一定有效。
5. 查看浏览器开发者工具 Network 中 `/api/auth/login` 的状态码和响应体。

### Token 在不同语言 API 之间无效

确认 Spring、Gin、FastAPI 等后端使用相同的 `API_SECRET`，并确认 Token 未过期。修改密钥后，重新登录获取 Token。

### CORS 错误

本地 Vite 代理模式先检查代理目标和后端是否运行。只有浏览器直接跨 origin 调用 API 时，才需要把页面 origin 加入 `CORS_ORIGINS`；origin 包含协议、主机和端口，不包含路径。

### 配置更改没有生效

修改 `.env` 后重启 Spring Boot。确认启动目录是 `versions/java/pms-springboot-api`，且没有同名系统环境变量覆盖 `.env` 的值。

## 10. 本地配置与生产配置的区别

本指南的 `.env` 和 `./start.sh` 适用于本地开发。生产环境不要使用默认管理员密码或默认 Token 密钥，也不要把 `.env` 复制进仓库或打包进 JAR。

生产部署时，应由 systemd、容器平台或密钥管理系统注入环境变量；数据库使用专用账号；API 放在 HTTPS 反向代理之后；CORS 仅允许真实前端来源。完整发布流程见[部署指南](deployment.md)。
