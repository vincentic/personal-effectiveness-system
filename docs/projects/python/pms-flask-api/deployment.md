# Flask API 版部署指南

Flask API 默认监听 `8002`，适配共享 React 前端。独立部署拓扑：

```text
React 静态站点 → Nginx HTTPS → Flask API / Gunicorn → MySQL
```

应用首次请求会初始化 schema 和空用户表的管理员。跨 API 共享 Token 时，`API_SECRET` 必须与对应 FastAPI、Go 或 Java API 完全一致。

## 1. Gunicorn + systemd

安装依赖并设置仅服务器可读的 `/opt/pms-flask-api/.env`：

```bash
python3 -m venv /opt/pms-flask-api/.venv
/opt/pms-flask-api/.venv/bin/pip install -r /opt/pms-flask-api/requirements.txt
chmod 600 /opt/pms-flask-api/.env
```

配置 `DB_*`、`SERVER_PORT=8002`、随机 `API_SECRET`、精确 `CORS_ORIGINS` 和强 `ADMIN_*`。创建 `/etc/systemd/system/pms-flask-api.service`：

```ini
[Unit]
Description=PMS Flask REST API
After=network.target

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms-flask-api
EnvironmentFile=/opt/pms-flask-api/.env
ExecStart=/opt/pms-flask-api/.venv/bin/gunicorn --workers 2 --bind 127.0.0.1:8002 app:app
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms-flask-api
sudo systemctl status pms-flask-api
journalctl -u pms-flask-api -f
```

## 2. Nginx、React 与 HTTPS

```nginx
server {
    listen 80;
    server_name api.example.com;

    location / {
        proxy_pass http://127.0.0.1:8002;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

将 React 构建产物部署为 HTTPS 静态站点，构建时指定 `VITE_API_BASE_URL=https://api.example.com`，并在 API 的 `CORS_ORIGINS` 中精确加入 React origin。若 Nginx 对外统一为同一 origin，可让 `/api` 和 `/health` 反代 API，其余路径由 Nginx 提供 React SPA。

## 3. Docker 与 Kubernetes

镜像内使用 Gunicorn 绑定 `0.0.0.0:8002`，配置通过环境变量传入。Kubernetes Secret 应承载 `API_SECRET`、`DB_PASSWORD` 和 `ADMIN_PASSWORD`；API Deployment 用 `/health` 探针，React 使用独立静态服务。完整 Dockerfile、Nginx Ingress、探针与发布示例见多语言 Docker + Kubernetes 部署指南。
