# Flask 单体版部署指南

Flask 单体版提供服务端页面，默认端口 `8001`。部署拓扑为：

```text
浏览器 → Nginx HTTPS → Gunicorn / Flask 页面 → MySQL
```

共享 v2 schema 在首次应用请求时幂等初始化。生产环境设置随机 `SECRET_KEY`、强管理员密码、最小权限数据库账号，并将 `SESSION_COOKIE_SECURE=true`。

## 1. Gunicorn + systemd

在服务器部署项目源码或构建好的应用目录，并安装依赖：

```bash
python3 -m venv /opt/pms-flask/.venv
/opt/pms-flask/.venv/bin/pip install -r /opt/pms-flask/requirements.txt
```

在 `/opt/pms-flask/.env` 配置 DB_*、`SECRET_KEY`、`SESSION_COOKIE_SECURE=true`、`ADMIN_*`，限制文件读取权限。创建 `/etc/systemd/system/pms-flask.service`：

```ini
[Unit]
Description=PMS Flask Monolith
After=network.target

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms-flask
EnvironmentFile=/opt/pms-flask/.env
ExecStart=/opt/pms-flask/.venv/bin/gunicorn --workers 2 --bind 127.0.0.1:8001 app:app
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms-flask
sudo systemctl status pms-flask
journalctl -u pms-flask -f
```

## 2. Nginx 与 HTTPS

```nginx
server {
    listen 80;
    server_name pms.example.com;

    location / {
        proxy_pass http://127.0.0.1:8001;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

生产应在 Nginx 或受信任的边缘入口启用 HTTPS，确认代理后的 Host 与 Cookie Secure 设置正确，再执行 `nginx -t` 和 reload。

## 3. Docker 与 Kubernetes

容器镜像使用 Gunicorn 绑定 `0.0.0.0:8001`，通过环境变量注入配置，不复制生产 `.env` 或密钥到镜像。Kubernetes 中优先使用 Secret 注入 `SECRET_KEY`、`DB_PASSWORD`、`ADMIN_PASSWORD`，MySQL 使用持久化或托管实例。完整 Dockerfile、Nginx Ingress、探针与发布示例见[多语言 Docker + Kubernetes 部署指南](../../../guides/container-deployment.md)。
