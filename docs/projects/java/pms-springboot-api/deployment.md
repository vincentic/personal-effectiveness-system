# PMS Spring Boot API 部署文档

本文说明 Spring Boot REST API 的 JAR 发布方式，以及 systemd、Nginx 与 MySQL 的配置。工程构建过程见[构建过程讲解](build-process-explained.md)，本地开发见[搭建指南](setup.md)。

## 1. 运行拓扑与运行环境

```text
Vue 静态站点 / API 客户端 → Nginx HTTPS → Spring Boot API → MySQL
```

运行服务器需要 JDK 17+、MySQL 8+、systemd 和 Nginx。Maven 只在构建机器需要。API 默认监听 `8080`；该项目不包含 Vue 前端，也不提供 Spring MVC 页面。

先创建数据库与专用账号；按实际连接来源配置 MySQL host：

```sql
CREATE DATABASE pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'pms'@'127.0.0.1' IDENTIFIED BY 'replace-with-a-strong-password';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
  ON pms.* TO 'pms'@'127.0.0.1';
```

启动时 Spring 执行 classpath 下的 `schema.sql` 幂等建表脚本。该脚本不会创建数据库，也不会替换已存在表的结构；表结构升级需另行管理。

## 2. 构建可执行 JAR

在仓库根目录或项目目录执行：

```bash
cd versions/java/pms-springboot-api
mvn -B clean package
```

产物为 `target/pms-springboot-api-1.0.0.jar`。可在构建机使用 JDK 17 和 Maven 生成，再将 JAR 复制到目标服务器；不要把 `target/` 作为源代码维护。

```bash
sudo install -d -o www-data -g www-data -m 0750 /opt/pms-springboot-api
sudo install -o www-data -g www-data -m 0640 \
  target/pms-springboot-api-1.0.0.jar \
  /opt/pms-springboot-api/pms-springboot-api.jar
```

## 3. 设置运行配置

创建 `/opt/pms-springboot-api/.env`：

```ini
DB_NAME=pms
DB_USER=pms
DB_PASSWORD=replace-with-a-strong-password
DB_HOST=127.0.0.1
DB_PORT=3306

SERVER_PORT=8080
API_SECRET=replace-with-a-long-random-secret
CORS_ORIGINS=https://pms.example.com

ADMIN_USERNAME=admin
ADMIN_PASSWORD=replace-with-a-unique-strong-password
```

Spring Boot 直接运行 JAR 时不会自行读取 `.env`；systemd 的 `EnvironmentFile` 会将这些值注入进程。`API_SECRET` 在需要跨版本共享 Token 时必须与 Gin / FastAPI 配置相同。仅允许真实 Vue origin 的 CORS 请求；管理员初始密码只在空用户表时生效。

```bash
sudo chown www-data:www-data /opt/pms-springboot-api/.env
sudo chmod 600 /opt/pms-springboot-api/.env
```

不要把真实密钥、数据库密码或生产 `.env` 加入版本控制。

## 4. 配置 systemd

创建 `/etc/systemd/system/pms-springboot-api.service`：

```ini
[Unit]
Description=PMS Spring Boot REST API
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms-springboot-api
EnvironmentFile=/opt/pms-springboot-api/.env
ExecStart=/usr/bin/java -jar /opt/pms-springboot-api/pms-springboot-api.jar
Restart=on-failure
RestartSec=5
KillSignal=SIGTERM
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
```

确认 Java 路径符合服务器实际安装位置，然后启用服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms-springboot-api
sudo systemctl status pms-springboot-api
journalctl -u pms-springboot-api -f
```

## 5. Nginx 反向代理和 HTTPS

```nginx
server {
    listen 80;
    server_name api.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

生产环境应通过 Nginx 或受信任的边缘入口启用 HTTPS，防火墙只开放必要端口。Vue 与 API 分开部署时，配置 `CORS_ORIGINS` 为 Vue 站点 origin；API 本身不托管静态前端。

```bash
sudo nginx -t
sudo systemctl reload nginx
```

## 6. 发布和验证

发布时构建新 JAR、复制到服务器，再重启 systemd 服务：

```bash
sudo systemctl stop pms-springboot-api
sudo install -o www-data -g www-data -m 0640 \
  target/pms-springboot-api-1.0.0.jar \
  /opt/pms-springboot-api/pms-springboot-api.jar
sudo systemctl start pms-springboot-api
sudo systemctl status pms-springboot-api
```

API 未配置 Actuator 健康端点或 OpenAPI 插件；可用无 Token 的受保护接口检查服务是否响应：

```bash
curl -i http://127.0.0.1:8080/api/me
```

预期返回 `401`。再调用 `POST /api/auth/login` 验证数据库、密码和管理员初始化状态。若启动失败，优先检查 `journalctl`、JDK 主版本、DB_*、端口占用、`API_SECRET` 与 CORS 设置。
