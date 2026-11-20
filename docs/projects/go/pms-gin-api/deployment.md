# PMS Gin API 部署文档

本文面向独立部署的 Gin REST API。Go 项目构建流程见[构建过程讲解](build-process-explained.md)，本地安装与启动见[搭建指南](setup.md)。此服务不托管 React 页面；页面可独立部署，也可用 FastAPI 项目同源托管。

## 1. 运行拓扑与前置条件

```text
React 静态站点 / API 客户端 → Nginx HTTPS → Gin API → MySQL
```

服务器需要 Linux、Go 1.22+（仅在服务器构建时需要）、MySQL 8+、systemd 和 Nginx。API 默认监听 `8081`，部署前先创建数据库和最小权限账号；数据库表由应用启动时执行的内嵌 `sql/schema.sql` 初始化。

```sql
CREATE DATABASE pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pms'@'127.0.0.1' IDENTIFIED BY 'replace-with-a-strong-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON pms.* TO 'pms'@'127.0.0.1';
```

如实际数据库连接不是本机 TCP 地址，应按数据库网络边界调整账号 host 与授权；不要将数据库暴露到公网。

## 2. 构建并安装程序

在仓库根目录执行测试和 Linux 目标构建；也可在兼容的目标服务器本机执行：

```bash
cd versions/go/pms-gin-api
go test ./...
mkdir -p build
GOOS=linux GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -o build/pms-gin-api .
```

上述命令示例面向 Linux x86-64；如目标为 ARM64，使用 `GOARCH=arm64`，或在目标服务器本机编译。将 `build/pms-gin-api` 复制到服务器，例如 `/opt/pms-gin-api/pms-gin-api`。部署目录不需要 Go 源码、模板或 React 文件；SQL schema 已编入二进制。

```bash
sudo install -d -o www-data -g www-data -m 0750 /opt/pms-gin-api
sudo install -o www-data -g www-data -m 0750 build/pms-gin-api /opt/pms-gin-api/pms-gin-api
```

## 3. 配置生产环境

创建 `/opt/pms-gin-api/.env`，只保存在服务器上并限制读取权限：

```ini
DB_NAME=pms
DB_USER=pms
DB_PASSWORD=replace-with-a-strong-password
DB_HOST=127.0.0.1
DB_PORT=3306

SERVER_PORT=8081
API_SECRET=replace-with-a-long-random-secret
CORS_ORIGINS=https://pms.example.com

ADMIN_USERNAME=admin
ADMIN_PASSWORD=replace-with-a-unique-strong-password
```

`API_SECRET` 与 Spring Boot / FastAPI API 共用 Token 时必须完全一致。`ADMIN_*` 只会在用户表为空时创建初始管理员。CORS 应配置为 React 站点的精确 origin；若前端和 API 由同一 origin 反向代理，则按实际浏览器请求路径配置并避免通配来源。保护 `.env`，不要提交到 Git。

```bash
sudo chown www-data:www-data /opt/pms-gin-api/.env
sudo chmod 600 /opt/pms-gin-api/.env
```

## 4. 配置 systemd

创建 `/etc/systemd/system/pms-gin-api.service`：

```ini
[Unit]
Description=PMS Gin REST API
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms-gin-api
EnvironmentFile=/opt/pms-gin-api/.env
ExecStart=/opt/pms-gin-api/pms-gin-api
Restart=on-failure
RestartSec=3
KillSignal=SIGTERM
TimeoutStopSec=15

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms-gin-api
sudo systemctl status pms-gin-api
journalctl -u pms-gin-api -f
```

## 5. 配置 Nginx 与 HTTPS

Nginx 可将流量反向代理到本机 API 端口。Gin 当前通过 `:SERVER_PORT` 监听所有网卡，因此必须用主机防火墙或云安全组禁止外部直接访问 `8081`，只允许 Nginx 转发：

```nginx
server {
    listen 80;
    server_name api.example.com;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

生产应在 Nginx 或受信任的边缘入口配置 HTTPS，并只开放必要的外部端口。React 与 API 使用不同 origin 时，将 React 地址配置到后端 `CORS_ORIGINS`，并在 React 构建时设置 `VITE_API_BASE_URL`。

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## 6. 发布与验证

每次发布先在兼容目标平台重新构建，通过安全的部署通道替换二进制，再重启服务：

```bash
sudo systemctl stop pms-gin-api
sudo install -o www-data -g www-data -m 0750 build/pms-gin-api /opt/pms-gin-api/pms-gin-api
sudo systemctl start pms-gin-api
sudo systemctl status pms-gin-api
```

Go API 当前未注册独立的 `/health` 路由；可用无 Token 的受保护端点确认服务已响应：

```bash
curl -i http://127.0.0.1:8081/api/me
```

预期得到 `401`，表示 HTTP 服务与路由正常、请求尚未认证。再以真实管理员凭据调用 `/api/auth/login` 验证数据库连接与认证。故障排查时查看 `journalctl -u pms-gin-api`，并核对 DB_*、`API_SECRET` 和 CORS origin。

## 7. Docker 与 Kubernetes

容器端口为 `8081`；API 没有匿名健康路由，Kubernetes 应使用 TCP 探针，不要用返回 401 的 `/api/me` 作为 HTTP probe。镜像构建、Nginx Ingress、Secret 和 React 路由见多语言 Docker + Kubernetes 部署指南。
