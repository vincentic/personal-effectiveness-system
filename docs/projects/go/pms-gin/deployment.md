# PMS Gin 单体版部署文档

基于 **Go 1.22+（本机实测 1.27.4，通过 gvm 安装）/ Gin 1.10 / database/sql / MySQL 8.0** 的项目管理系统 Gin 版。
本目录仅提供服务端页面；独立 REST API 项目见 [`../pms-gin-api/`](../../../../versions/go/pms-gin-api/README.md)。

---

## 一、系统功能

- 签名 Cookie 会话 + CSRF 防护的登录 / 退出
- 工作台：项目、任务统计概览，我的项目与我的待办
- 项目管理：新建、编辑、删除、筛选、成员管理、实时进度
- 任务管理：三列看板，指派执行人、优先级、截止日期
- 用户管理：仅管理员可访问 `/admin/users/`

## 二、目录结构

```
versions/go/pms-gin/
├── go.mod / go.sum                  # Go 模块（gin / mysql / x/crypto）
├── main.go                          # 入口：embed 资源、初始化、启动
├── .env.example                     # 环境变量示例
├── start.sh                         # 便捷启动（自动 tidy、加载 .env）
├── ../../../docs/projects/go/pms-gin/deployment.md # 本文（集中归档）
├── sql/schema.sql                   # 内嵌建表脚本（go:embed）
├── internal/
│   ├── config/                      # .env 加载与配置
│   ├── database/                    # database/sql 通用查询
│   ├── models/                      # 视图模型 + 全部 SQL
│   └── server/                      # 会话/CSRF、模板渲染与页面 handler
├── templates/                       # html/template 页面（go:embed）
└── static/css/style.css             # 复用 Django 版样式（go:embed）
```

> 页面、SQL、静态资源均通过 `go:embed` 编译进单个二进制，部署时只需分发二进制 + `.env`，无需带 templates/static/sql 目录。

## 三、环境准备

| 软件 | 版本要求 | 备注 |
|------|---------|------|
| Go | 1.22+ | 本机用 gvm 装 1.27.4 |
| MySQL | 8.0+（5.7 亦可） | 与其他版共用库 |
| Nginx（生产可选） | 1.18+ | 反向代理 / HTTPS |

### 1. 安装 Go（gvm 方式，本机已装）

```bash
# 安装 gvm（若 raw.githubusercontent.com 不通，可从 codeload 取源码）
curl -sS -L https://github.com/moovweb/gvm/raw/master/binscripts/gvm-installer | bash
source "$HOME/.gvm/scripts/gvm"

# 安装稳定版 Go（二进制方式，避免源码编译）
gvm install go1.27.4 --binary
gvm use go1.27.4 --default
go version
```

国内拉模块建议设代理：

```bash
go env -w GOPROXY=https://goproxy.cn,direct
```

### 2. 创建数据库

```bash
mysql -u root -p
```

```sql
CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pms'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON pms.* TO 'pms'@'%';
FLUSH PRIVILEGES;
```

## 四、部署步骤

### 1. 配置环境变量

```bash
cd versions/go/pms-gin
cp .env.example .env
vim .env
```

生产环境务必修改：

```ini
DB_NAME=pms
DB_USER=pms
DB_PASSWORD=your-password
DB_HOST=127.0.0.1
DB_PORT=3306

SERVER_PORT=8081

SESSION_SECRET=换成另一段随机长字符串
COOKIE_SECURE=true          # 生产走 HTTPS 时设为 true；本地开发保持 false

ADMIN_USERNAME=admin
ADMIN_PASSWORD=换成强密码
```

> `COOKIE_SECURE=true` 时页面登录 Cookie 仅在 HTTPS 下传输；若前面没有终止 TLS 的 Nginx，请勿开启，否则页面无法登录。

### 2. 本地开发运行

```bash
./start.sh
# 或
go mod tidy && go run .
```

访问 <http://127.0.0.1:8081> 。启动时自动执行内嵌 `schema.sql` 幂等建表，`users` 表为空则创建默认管理员。

### 3. 编译生产二进制

```bash
CGO_ENABLED=0 go build -o pms-gin .
# 产物 pms-gin 为静态二进制，直接分发即可（已内嵌 templates/static/sql）
```

## 五、生产环境部署（Linux + systemd + Nginx）

### 1. 部署目录

```bash
sudo mkdir -p /opt/pms-gin
# 编译好后上传二进制与 .env
sudo cp pms-gin /opt/pms-gin/pms-gin
sudo cp .env /opt/pms-gin/.env
```

### 2. systemd 服务

`/etc/systemd/system/pms-gin.service`：

```ini
[Unit]
Description=PMS Gin Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms-gin
EnvironmentFile=-/opt/pms-gin/.env

ExecStart=/opt/pms-gin/pms-gin

KillSignal=TERM
TimeoutStopSec=15
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms-gin
sudo systemctl status pms-gin
journalctl -u pms-gin -f
```

### 3. Nginx 反向代理

`/etc/nginx/conf.d/pms-gin.conf`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 10M;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

静态资源已内嵌并由 Gin 自身提供，无需单独 location。
HTTPS 在此层终止；若开了 `COOKIE_SECURE=true`，务必确保 `X-Forwarded-Proto` 正确传递。

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 4. 更新发布

```bash
# 本机重新编译后上传
CGO_ENABLED=0 go build -o pms-gin .
scp pms-gin server:/opt/pms-gin/pms-gin
ssh server 'sudo systemctl restart pms-gin'
```

## 六、常见问题排查

| 问题 | 解决方法 |
|------|---------|
| `go: module ... not found` / 拉包超时 | 设 `go env -w GOPROXY=https://goproxy.cn,direct`，再 `go mod tidy` |
| `go: command not found` | 加载 gvm：`source "$HOME/.gvm/scripts/gvm" && gvm use go1.27.4` |
| `Access denied` / 连不上库 | 检查 `.env` 的 DB_* ；确认 MySQL 账号权限与来源主机 |
| 页面登录总是跳回 /login | 若开了 `COOKIE_SECURE=true` 但没走 HTTPS，Cookie 不会被浏览器保存；本地设为 false |
| CSRF 403 | POST 表单需带 `_csrf` 隐藏字段；确认 `pms_csrf` Cookie 正常写入 |
| 端口被占用 | `lsof -nP -iTCP:8081 -sTCP:LISTEN`，或用 `SERVER_PORT` 改端口 |
| 路由 `/projects/new/` 行为异常 | 该版本因 Gin 路由树限制，把 new 合入 `/projects/:id/` 内部按 id=="new" 分流，对外行为一致 |

## 七、验证清单

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/login    # 200
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/         # 302 to /login when logged out
```
