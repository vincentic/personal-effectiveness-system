# PMS Gin API 构建过程讲解

本文说明独立 Go/Gin REST API 的依赖、编译、配置加载和验证流程。API 端点与共享数据结构见[API 契约](../../../data/api-contract.md)；本地搭建见[搭建指南](setup.md)，生产运行见[部署指南](deployment.md)。

## 1. 工程边界与请求链路

[`go.mod`](../../../../versions/go/pms-gin-api/go.mod) 定义独立的 `pms-gin-api` module。它实现 REST API，不渲染 Vue 页面，也不依赖 Gin 页面单体版的模板和静态文件。共享 Vue 前端位于 `versions/vue/pms-vue/`，可通过 Vite 开发代理连接 API。

```text
Vue / API 客户端 → Gin 路由 → Token 校验与 CORS → database/sql → MySQL
```

`main.go` 加载运行配置、打开数据库、执行嵌入的建表 SQL，再创建 Gin 路由并监听 `SERVER_PORT`。`internal/server` 实现认证中间件、CORS 和资源路由；`internal/models` 负责数据访问。

## 2. 获取依赖与编译

```bash
cd versions/go/pms-gin-api
go mod download
go test ./...
go vet ./...
mkdir -p build
CGO_ENABLED=0 go build -trimpath -o build/pms-gin-api .
```

依赖版本由 `go.mod` 与 `go.sum` 固定。仅在改变依赖时运行 `go mod tidy` 并审阅这两个文件的差异。当前模块将 `sql/schema.sql` 通过 `go:embed` 编入程序，运行时不需要单独分发 SQL 文件；与数据库建立网络连接仍是运行前置条件。

`go build` 的产物为 `build/pms-gin-api`。前端构建不属于该 Go module；部署共享 Vue 时，应分别构建和托管前端，或使用现成的 FastAPI 同源前端项目。

以上命令生成与构建机平台匹配的产物。跨平台发布到 Linux x86-64 时，将构建命令改为 `GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -o build/pms-gin-api .`；ARM64 目标应使用 `GOARCH=arm64`。

## 3. API 配置如何进入运行进程

[`internal/config/config.go`](../../../../versions/go/pms-gin-api/internal/config/config.go) 加载 `.env`，但系统环境变量优先。常见配置包括：

| 配置 | 作用 |
|---|---|
| `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD` | MySQL 连接 |
| `SERVER_PORT` | Gin HTTP 监听端口，默认 `8081` |
| `API_SECRET` | Bearer Token 的 HMAC 签名密钥 |
| `CORS_ORIGINS` | 允许访问 API 的 Vue 前端 origin 列表 |
| `ADMIN_USERNAME`、`ADMIN_PASSWORD` | 仅在 `users` 表为空时初始化管理员 |

当 Gin、Spring Boot 与 FastAPI 需要互认 Token 时，三端必须配置相同的 `API_SECRET`。生产环境必须换掉示例密钥和密码，并将 CORS 收敛到实际前端来源。

## 4. 启动和验收

本地开发可用 `./start.sh` 加载 `.env` 后运行源码；已构建版本运行 `./build/pms-gin-api`。启动日志中的数据库错误应先通过 DB_* 配置与 MySQL 连通性排查。

```bash
curl -i http://127.0.0.1:8081/api/me
curl -s -X POST http://127.0.0.1:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"你的密码"}'
```

无 Token 的 `/api/me` 预期返回 `401`，表示 HTTP 服务与路由正常、请求尚未认证；再调用登录端点验证数据库与初始管理员。完整发布文件、systemd 和 Nginx 反向代理配置见[部署指南](deployment.md)。
