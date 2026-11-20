# PMS Spring Boot 单体版部署文档

基于 **JDK 17 / Spring Boot 3.2.5 / Maven 3.8+ / MySQL 8.0** 的个人效能系统 Spring Boot 版（v2）。
本目录仅提供日/周/月服务端页面；独立 REST API 项目见 [`../pms-springboot-api/`](../../../../versions/java/pms-springboot-api/README.md)。构建过程详见[构建过程讲解](build-process-explained.md)。

---

## 一、系统功能

- 表单登录 / 退出（Spring Security + Session + CSRF）
- 工作台：日/周/月清单数量、今日入口、近 7 日时间盒汇总
- 每日执行清单：一人一天一条，4 个时间盒 + 13 项检查 + 晚间复盘
- 每周复盘清单：一人一周一条，下周设计 + 5 问 + 4 领域 + 8 任务流
- 每月重构清单：一人一月一条，价值检查 + 三项改进 + 6 模块健康检查
- 明细子表随主表整体替换；所有清单按用户隔离，跨用户不可见
- 用户管理：仅管理员可访问 `/admin/users/`

## 二、目录结构

```
versions/java/pms-springboot/
├── pom.xml                          # Maven 构建（Spring Boot 3.2.5 / Java 17）
├── .env.example                     # 环境变量示例（复制为 .env，start.sh 自动加载）
├── start.sh                         # 便捷启动脚本
├── sql/schema.sql                   # 建表脚本副本（与 db/schema.sql 一致）
└── src/main/
    ├── java/com/example/pms/        # 应用源码
    │   ├── config/                  # SecurityConfig（页面 Session 与权限）
    │   ├── domain/ repo/            # 实体与数据访问（JdbcTemplate）
    │   ├── support/                 # 页面辅助与 AppInitializer（建表/管理员）
    │   └── web/                     # 页面 Controller 与 DTO
    └── resources/
        ├── application.yml          # 数据源、Thymeleaf、sql.init、pms 配置
        ├── schema.sql               # 启动时幂等建表（spring.sql.init）
        ├── templates/               # Thymeleaf 页面
        └── static/css/style.css     # 复用 Django 版样式
```

## 三、环境准备

| 软件 | 版本要求 | 备注 |
|------|---------|------|
| JDK | 17+ | Spring Boot 3.2 最低要求 |
| Maven | 3.8+ | 或使用仓库自带 `mvnw`（若提供） |
| MySQL | 8.0+（5.7 亦可） | 与 Django 版共用同一库 |
| Nginx（生产可选） | 1.18+ | 反向代理 / HTTPS |

### 1. 安装 JDK 与 Maven

```bash
# macOS（Homebrew）
brew install openjdk@17 maven

# Ubuntu / Debian
sudo apt update && sudo apt install -y openjdk-17-jdk maven
java -version    # 确认 17+
mvn -v
```

### 2. 创建数据库

与其他版本共用一个库即可，表结构由应用启动时自动创建。

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
cd versions/java/pms-springboot
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

SERVER_PORT=8080

ADMIN_USERNAME=admin
ADMIN_PASSWORD=换成强密码
```

> `.env` 由 `start.sh` 加载；若用 `mvn`/`java -jar` 直接启动，请改用系统环境变量或在 `application.yml` 中注入。系统环境变量优先于 `.env`。

### 2. 本地开发运行

```bash
./start.sh
# 或
mvn spring-boot:run
```

浏览器访问 <http://127.0.0.1:8080> ，用 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 登录。
`spring.sql.init.mode=always` 会在每次启动时执行幂等 `schema.sql`，`users` 表为空时自动创建默认管理员。

### 3. 打包与运行

```bash
mvn -B clean package
# 产物：target/pms-springboot-1.0.0.jar
java -jar target/pms-springboot-1.0.0.jar --server.port=8080
```

## 五、生产环境部署（Linux + systemd + Nginx）

### 1. 部署目录

```bash
sudo mkdir -p /opt/pms-springboot
sudo cp -r versions/java/pms-springboot/. /opt/pms-springboot/
cd /opt/pms-springboot
mvn -B clean package
```

### 2. systemd 服务

`/etc/systemd/system/pms-springboot.service`：

```ini
[Unit]
Description=PMS Spring Boot Service
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms-springboot
EnvironmentFile=-/opt/pms-springboot/.env

ExecStart=/usr/bin/java -jar /opt/pms-springboot/target/pms-springboot-1.0.0.jar --server.port=8080

KillSignal=TERM
TimeoutStopSec=30
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms-springboot
sudo systemctl status pms-springboot
journalctl -u pms-springboot -f       # 查看日志
```

### 3. Nginx 反向代理

`/etc/nginx/conf.d/pms-springboot.conf`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 10M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

静态资源由 Spring Boot 自身提供（`/static/**`），无需单独配置 location。
如需 HTTPS，在此层终止 TLS 并加 `listen 443 ssl` 与证书配置。

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 4. 更新发布

```bash
cd /opt/pms-springboot
git pull
mvn -B clean package -DskipTests
sudo systemctl restart pms-springboot
```

## 六、常见问题排查

| 问题 | 解决方法 |
|------|---------|
| `Communications link failure` / `Access denied` | 检查 `.env` 的 DB_* 是否正确；确认 MySQL 账号有 pms 库权限且允许来源主机 |
| `Unknown database 'pms'` | 先执行第三节建库 SQL |
| `Unsupported class file major version` | JDK 版本不对，需 JDK 17+；macOS 可用 `/usr/libexec/java_home -v 17` |
| 登录后跳转异常 / CSRF 403 | 确认经 Nginx 时传递了 `X-Forwarded-Proto`，且表单提交了 `_csrf` 隐藏字段 |
| 改了配置不生效 | 系统环境变量优先于 `.env`；检查是否有残留环境变量覆盖 |
| 端口被占用 | `lsof -nP -iTCP:8080 -sTCP:LISTEN` 查进程，或用 `SERVER_PORT` 改端口 |

## 七、验证清单

```bash
curl -s http://127.0.0.1:8080/                    # 未登录应 302 到 /login
```

## 八、Docker 与 Kubernetes

容器端口仍为 `8080`，服务端页面可通过 `/` HTTP 探针检查。Java 多阶段镜像、Nginx Ingress、Session 配置、Secret 与 rollout 步骤见[多语言 Docker + Kubernetes 部署指南](../../../guides/container-deployment.md)。
