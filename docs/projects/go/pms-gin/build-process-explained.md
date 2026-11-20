# PMS Gin 单体版构建过程讲解

本文说明 Go Gin 页面单体版从依赖解析、资源嵌入到生成可部署程序的过程。环境准备见[搭建指南](setup.md)，生产发布见[部署指南](deployment.md)。

## 1. 工程边界

[`go.mod`](../../../../versions/go/pms-gin/go.mod) 将此项目作为独立 Go module 管理，直接依赖 Gin、MySQL 驱动和 `x/crypto`。该目录提供服务端页面，不包含 REST API；API 项目位于 [`pms-gin-api`](../pms-gin-api/setup.md)，两者复用数据库结构但分别构建和运行。

运行链路为：

```text
浏览器 → Gin 路由与页面处理器 → database/sql → MySQL
                         └→ html/template 渲染页面
```

配置、数据库访问和页面处理分别放在 `internal/config`、`internal/database`、`internal/models` 与 `internal/server`。`main.go` 负责加载配置、初始化数据库和启动 HTTP 服务。

## 2. 依赖解析与资源组织

在项目目录中，Go 根据 `go.mod` 与 `go.sum` 获取并校验依赖：

```bash
cd versions/go/pms-gin
go mod download
```

首次新增或删除直接依赖时，使用 `go mod tidy` 同步模块清单；普通 CI 构建使用仓库已提交的 `go.mod` / `go.sum`，避免无意改变依赖版本。

`main.go` 使用 `go:embed` 将 `templates/`、`static/` 和 `sql/schema.sql` 编入程序。页面模板、样式和建表脚本因此不是部署时的外部文件；运行时仍需要可访问的 MySQL 和正确的环境配置。

## 3. 编译与检查

```bash
go test ./...
go vet ./...
mkdir -p build
CGO_ENABLED=0 go build -trimpath -o build/pms-gin .
```

- `go test ./...` 编译并运行模块内测试。
- `go vet ./...` 检查常见的 Go 代码问题。
- `go build` 从当前 module 的 `main` package 生成可执行文件；关闭 CGO 可减少目标环境的运行时依赖。操作系统和 CPU 架构仍由构建机平台或显式的 `GOOS` / `GOARCH` 决定。
- `-trimpath` 避免将构建机器的源码绝对路径写入产物。

构建产物是 `build/pms-gin`。Go 不会在这里生成 WAR/JAR 或独立静态资源目录；资源已经由 `go:embed` 包含在二进制中。

上面的命令生成与当前构建机操作系统和架构匹配的二进制。若从 macOS / Windows 为 Linux x86-64 发布，可改为：

```bash
GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -o build/pms-gin .
```

目标为 ARM64 时将 `GOARCH` 改为 `arm64`；也可直接在与目标相同的平台构建。

## 4. 配置和启动阶段

[`internal/config/config.go`](../../../../versions/go/pms-gin/internal/config/config.go) 读取进程环境变量和当前工作目录下的 `.env`；已存在的系统环境变量优先。启动时数据库模块读取嵌入的 `schema.sql` 并执行幂等初始化，空用户表才会创建配置的初始管理员。

本地可用 `./start.sh` 加载 `.env` 并启动开发服务；生产环境将二进制与 `.env` 一起部署，由 systemd 管理进程。不要把示例管理员密码、数据库密码或生产会话密钥写入二进制或提交到版本库。

## 5. 验收构建结果

```bash
./build/pms-gin
```

确认服务启动日志无数据库错误后，检查登录页与未登录首页行为：

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/login
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/
```

预期登录页返回 `200`，未登录访问受保护首页会重定向到登录页。具体环境变量、systemd 与 Nginx 配置见[部署指南](deployment.md)。
