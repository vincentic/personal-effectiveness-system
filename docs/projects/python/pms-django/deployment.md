# 项目管理系统部署文档

基于 **Python 3.10+ / Django 4.2 / MySQL 8.0** 的简单项目管理系统。构建与迁移过程详见[构建过程讲解](build-process-explained.md)。

---

## 一、系统功能

- 用户登录 / 退出（Django 内置认证）
- 工作台：项目、任务统计概览，我的项目与我的待办
- 项目管理：新建、编辑、删除、按状态/名称筛选，成员管理，任务进度条
- 任务管理：三列看板（待处理 / 进行中 / 已完成），指派执行人、优先级、截止日期
- Django Admin 后台管理

## 二、目录结构

```
versions/python/pms-django/ # 仓库内按服务端语言归档的 Django 版目录
├── manage.py                 # Django 管理入口
├── requirements.txt          # Python 依赖
├── .env.example              # 环境变量示例（复制为 .env 使用）
├── README.md                 # 项目说明（另有 zh-CN / zh-TW / en 多语言版）
├── deploy/                   # 生产部署与热部署配置
│   ├── gunicorn.conf.py      # Gunicorn 生产配置（优雅重载参数）
│   ├── pms.service           # systemd 单元（含 ExecReload）
│   ├── hot-deploy.sh         # 生产一键热部署脚本
│   └── local-hot.sh          # 本地命令行热部署 / watch
├── config/                   # 项目配置
│   ├── __init__.py           # PyMySQL → MySQLdb 兼容层
│   ├── settings.py           # 全局配置（读取 .env）
│   ├── urls.py               # 根路由
│   └── wsgi.py
├── projects/                 # 核心应用
│   ├── models.py             # Project / Task 模型
│   ├── views.py              # 视图（登录、工作台、项目、任务）
│   ├── forms.py, admin.py, urls.py
│   └── migrations/
├── templates/                # 页面模板（Bootstrap 5）
└── static/css/style.css      # 自定义样式
```

## 三、环境准备

| 软件 | 版本要求 |
|------|---------|
| Python | 3.10+ |
| MySQL | 8.0+（5.7 亦可） |
| Nginx（生产可选） | 1.18+ |

### 1. 安装 MySQL

```bash
# macOS
brew install mysql && brew services start mysql

# Ubuntu / Debian
sudo apt update && sudo apt install -y mysql-server
sudo systemctl enable --now mysql
```

### 2. 创建数据库

```bash
mysql -u root -p
```

```sql
CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 建议创建专用账号（把 your-password 换成自己的密码）
CREATE USER 'pms'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON pms.* TO 'pms'@'%';
FLUSH PRIVILEGES;
```

## 四、部署步骤

### 1. 获取代码并创建虚拟环境

```bash
cd pms/versions/python/pms-django
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### 2. 配置环境变量

```bash
cp .env.example .env
vim .env
```

生产环境务必修改：

```ini
DEBUG=False
SECRET_KEY=换成一段随机长字符串
DB_NAME=pms
DB_USER=pms
DB_PASSWORD=your-password
DB_HOST=127.0.0.1
DB_PORT=3306
```

### 3. 初始化数据库并创建管理员

```bash
python manage.py makemigrations projects
python manage.py migrate
python manage.py createsuperuser   # 按提示输入用户名/邮箱/密码
```

### 4. 本地开发运行

```bash
python manage.py runserver 0.0.0.0:8000
```

浏览器访问 http://127.0.0.1:8000 ，登录后即可使用。
后台地址：http://127.0.0.1:8000/admin/

## 五、生产环境部署（Linux + Gunicorn + Nginx）

### 1. 收集静态文件

```bash
python manage.py collectstatic --noinput
```

### 2. 用 Gunicorn 启动服务

推荐直接使用仓库中的生产配置（含优雅重载参数，详见 [热部署指南](hot-deployment.md)）：

```bash
pip install gunicorn                 # requirements.txt 已包含
gunicorn -c deploy/gunicorn.conf.py config.wsgi:application
```

建议写成 systemd 服务 `/etc/systemd/system/pms.service`，内容可直接复制 `deploy/pms.service`：

```ini
[Unit]
Description=PMS Django Service (Gunicorn)
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms
Environment="PATH=/opt/pms/.venv/bin"
Environment="DJANGO_SETTINGS_MODULE=config.settings"
Environment="GUNICORN_PIDFILE=/run/pms/gunicorn.pid"
EnvironmentFile=-/opt/pms/.env

RuntimeDirectory=pms
RuntimeDirectoryMode=0755
PIDFile=/run/pms/gunicorn.pid

ExecStart=/opt/pms/.venv/bin/gunicorn -c /opt/pms/deploy/gunicorn.conf.py config.wsgi:application
ExecReload=/bin/kill -HUP $MAINPID

KillMode=mixed
KillSignal=TERM
TimeoutStopSec=75
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

`ExecReload` 是热部署的关键：`systemctl reload pms` 会向 master 发送 HUP，新 worker 加载新代码，旧 worker 处理完在途请求后退出，服务不中断。

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms
```

### 3. Nginx 反向代理

`/etc/nginx/conf.d/pms.conf`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 10M;

    location /static/ {
        alias /opt/pms/staticfiles/;
    }

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

上例只展示反向代理骨架。生产启用 Secure Cookie 时还必须配置 HTTPS 证书和 `listen 443 ssl`，并在 TLS 入口设置 `X-Forwarded-Proto $scheme`；HTTP 80 应重定向到 HTTPS。不能让 `DEBUG=False` 且 Secure Cookie 开启的登录站点只通过纯 HTTP 提供。

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 4. 热部署（不停机发布）

日常更新代码不要用 `systemctl restart`（会中断连接），改用 Gunicorn 的 HUP 优雅重载：

```bash
cd /opt/pms
./deploy/hot-deploy.sh --dry-run    # 先演练，只打印命令
./deploy/hot-deploy.sh              # 正式发布
```

脚本依次完成代码拉取、依赖安装、`check` / `migrate` / `collectstatic`、`systemctl reload pms` 与 `/login/` 健康检查；任一步失败即中止且不会触发重载。新旧 worker 会短暂共存，Nginx 上游端口不变，因此无需 reload Nginx。

> 关键约束：`deploy/gunicorn.conf.py` 中必须保持 `preload_app = False`；数据库迁移需向后兼容；含破坏性迁移时改用蓝绿部署。完整原理、参数、回滚与排错见 [热部署指南](hot-deployment.md)。

## 六、Docker + Nginx 部署

此方案使用两个镜像：Django/Gunicorn 应用镜像，以及包含同一版本静态文件的非特权 Nginx 镜像。MySQL 可使用外部托管实例；下面的 Compose 示例另含单机演示用 MySQL。生产数据应使用受管数据库或可靠的持久化存储。

### 1. 生产配置检查

当前 [`config/settings.py`](../../../../versions/python/pms-django/config/settings.py) 将 `ALLOWED_HOSTS` 固定为 `["*"]`，尚未从环境变量读取 `CSRF_TRUSTED_ORIGINS`，也未配置反向代理 HTTPS 识别和 Secure Cookie。公开部署前应在该文件中将对应配置改为环境变量驱动，例如：

```python
ALLOWED_HOSTS = [
    host.strip()
    for host in _env("ALLOWED_HOSTS", "localhost").split(",")
    if host.strip()
]
CSRF_TRUSTED_ORIGINS = [
    origin.strip()
    for origin in _env("CSRF_TRUSTED_ORIGINS", "").split(",")
    if origin.strip()
]
SECURE_PROXY_SSL_HEADER = ("HTTP_X_FORWARDED_PROTO", "https")
SESSION_COOKIE_SECURE = not DEBUG
CSRF_COOKIE_SECURE = not DEBUG
```

不要只在 Compose 或 Kubernetes 中设置 `ALLOWED_HOSTS` / `CSRF_TRUSTED_ORIGINS` 而不更新 Django settings；当前代码不会读取它们。部署域名、HTTPS 和反向代理 header 应保持一致。

### 2. 创建 Dockerfile、Nginx 配置和忽略文件

在 `versions/python/pms-django/` 下创建 `Dockerfile`。`django` target 运行 Gunicorn 并收集静态文件；`nginx` target 复用同一构建阶段生成的静态文件，避免应用与静态资源版本不一致。

```dockerfile
FROM python:3.12-slim AS django

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DJANGO_SETTINGS_MODULE=config.settings \
    GUNICORN_BIND=0.0.0.0:8000 \
    GUNICORN_PIDFILE=/tmp/gunicorn.pid

WORKDIR /app
RUN groupadd --system --gid 10001 pms \
    && useradd --system --uid 10001 --gid pms --home-dir /app pms

COPY requirements.txt ./
RUN python -m pip install --no-cache-dir -r requirements.txt

COPY --chown=pms:pms . .
RUN DEBUG=False python manage.py collectstatic --noinput

USER 10001:10001
EXPOSE 8000
CMD ["gunicorn", "-c", "deploy/gunicorn.conf.py", "config.wsgi:application"]

FROM nginxinc/nginx-unprivileged:stable-alpine AS nginx
COPY --from=django /app/staticfiles/ /usr/share/nginx/html/static/
COPY deploy/nginx-container.conf /etc/nginx/conf.d/default.conf
```

`GUNICORN_BIND=0.0.0.0:8000` 是容器间通信所必需的：仓库中的 Gunicorn 默认绑定 `127.0.0.1`，默认值只适用于同一主机上的 Nginx。PID 文件改放到容器可写的 `/tmp`。正式环境应根据组织的镜像策略固定基础镜像版本或 digest，并定期重建更新安全补丁。

创建 `deploy/nginx-container.conf`：

```nginx
server {
    listen 8080;
    server_name _;
    client_max_body_size 10m;

    location /static/ {
        alias /usr/share/nginx/html/static/;
        access_log off;
        expires 7d;
    }

    location / {
        proxy_pass http://web:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
    }
}
```

Nginx 使用 8080 高位端口运行，适配非特权镜像。Compose 中应用服务名为 `web`，因此该配置用 Docker DNS 转发到 `web:8000`。Kubernetes Pod 内的两个容器共享网络命名空间，后文会用 ConfigMap 将上游改为 `127.0.0.1:8000`。Docker Compose 的 8080 端口只绑定到宿主机 loopback；受信任的外层 Nginx 或负载均衡负责设置 `X-Forwarded-Proto`，不能直接把客户端提交的同名 header 当作可信值。

创建 `.dockerignore`，防止把本地环境、密钥和缓存复制进镜像：

```text
.git
.venv
__pycache__/
*.py[cod]
.env
.env.*
!.env.example
staticfiles/
```

### 3. 构建和检查镜像

在 `versions/python/pms-django/` 执行。将 registry 地址和不可变版本标签替换为自己的值：

```bash
docker build --target django -t registry.example.com/pms-django:1.0.0 .
docker build --target nginx -t registry.example.com/pms-django-nginx:1.0.0 .
```

推送两个镜像到目标环境可访问的镜像仓库：

```bash
docker push registry.example.com/pms-django:1.0.0
docker push registry.example.com/pms-django-nginx:1.0.0
```

镜像标签应与同一个代码提交绑定；不要用会漂移的 `latest` 作为生产发布版本。实际部署前应按需执行 `python manage.py check --deploy`，解决 Django 报告的生产配置告警。
示例 Nginx tag 用于说明非特权镜像；生产构建应替换为已验证的具体版本并锁定 digest，确保可重复发布。

### 4. Docker Compose 单机部署

创建不纳入 Git 的 `.env.compose`（权限设为 `600`）。此文件用于 Compose 插值和容器环境：

```ini
DEBUG=False
SECRET_KEY=replace-with-a-long-random-secret
DB_NAME=pms
DB_USER=pms
DB_PASSWORD=replace-with-a-strong-database-password
DB_HOST=db
DB_PORT=3306
MYSQL_ROOT_PASSWORD=replace-with-a-different-root-password
ALLOWED_HOSTS=pms.example.com
CSRF_TRUSTED_ORIGINS=https://pms.example.com
```

下面的 `compose.yaml` 在 MySQL 有健康响应后才启动 Web，并把对外流量交给 Nginx：

```yaml
services:
  db:
    image: mysql:8.0
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: ${DB_NAME}
      MYSQL_USER: ${DB_USER}
      MYSQL_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD in .env.compose}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?set MYSQL_ROOT_PASSWORD in .env.compose}
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1", "--silent"]
      interval: 10s
      timeout: 5s
      retries: 10

  web:
    build:
      context: .
      target: django
    restart: unless-stopped
    env_file: .env.compose
    environment:
      DB_HOST: db
      GUNICORN_BIND: 0.0.0.0:8000
      GUNICORN_PIDFILE: /tmp/gunicorn.pid
    depends_on:
      db:
        condition: service_healthy
    expose:
      - "8000"

  nginx:
    build:
      context: .
      target: nginx
    restart: unless-stopped
    depends_on:
      - web
    ports:
      - "127.0.0.1:8080:8080"

volumes:
  mysql_data:
```

使用 `docker compose --env-file .env.compose` 前缀执行以下步骤：

```bash
chmod 600 .env.compose
docker compose --env-file .env.compose build
docker compose --env-file .env.compose up -d db
docker compose --env-file .env.compose run --rm web \
  python manage.py migrate --noinput
docker compose --env-file .env.compose up -d web nginx
docker compose --env-file .env.compose exec nginx nginx -t
docker compose --env-file .env.compose run --rm web \
  python manage.py createsuperuser
docker compose --env-file .env.compose ps
docker compose --env-file .env.compose logs -f web nginx
```

先迁移、后启动 Web。静态文件已在 Django 镜像构建阶段收集，并同步复制进同版本 Nginx 镜像。管理员通过 Django 管理命令创建。线上若使用外部 MySQL，应删除 `db` 服务和 `mysql_data` volume，并将 `DB_HOST` 指向私网数据库地址。

在宿主机 Nginx 配置 TLS 并反向代理到 loopback 8080；示例如下。因为只有宿主机 Nginx 能访问该端口，它会覆盖外部传入的 `X-Forwarded-Proto`，Django 才能安全识别 HTTPS：

```nginx
server {
    listen 80;
    server_name pms.example.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name pms.example.com;
    ssl_certificate /etc/letsencrypt/live/pms.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/pms.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

不要将 Gunicorn 8000、Compose Nginx 8080 或 MySQL 3306 映射到公网；公网只开放 HTTPS 入口。
完成宿主机 Nginx TLS 配置后，可用 `curl -I https://pms.example.com/login/` 检查外部 HTTPS 路径。

## 七、Kubernetes + Nginx Ingress 部署

Kubernetes 示例将 Django/Gunicorn 与静态文件 Nginx 作为同一个 Pod 的两个容器；外部 Nginx Ingress Controller 负责 TLS、域名路由和负载均衡。MySQL 使用集群外的受管实例或已单独运维的 Stateful 服务，不把演示型 MySQL Pod 用作生产数据库。

```text
浏览器 --HTTPS--> Nginx Ingress Controller
                         |
                    ClusterIP Service
                         |
               Pod: Nginx sidecar --> Gunicorn / Django
                         |
                 私网 / 受管 MySQL
```

#### 1. 准备集群、Ingress Controller 和镜像

先准备 Kubernetes 集群、kubectl、Helm、可供集群拉取的镜像仓库，以及私网 MySQL。安装 Nginx Ingress Controller：

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm repo update
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
kubectl get service -n ingress-nginx
```

生产集群应根据集群版本选定并固定已验证的 Helm chart 版本，不要在无人审阅的情况下自动升级 Ingress Controller。

将 Nginx Ingress Service 的外部地址配置到域名 DNS。使用云厂商 LoadBalancer 时等待 EXTERNAL-IP；裸机集群需先配置 MetalLB 等 LoadBalancer 实现，或按集群规范使用 NodePort。

构建并推送两个镜像。示例用 `1.0.0`；实际应使用 Git commit SHA 或发行版本，并在两个镜像上保持同一标签：

```bash
cd versions/python/pms-django
docker build --target django -t registry.example.com/pms-django:1.0.0 .
docker build --target nginx -t registry.example.com/pms-django-nginx:1.0.0 .
docker push registry.example.com/pms-django:1.0.0
docker push registry.example.com/pms-django-nginx:1.0.0
```

#### 2. 创建 Namespace、Secret 和 ConfigMap

```bash
kubectl create namespace pms
```

在受保护的本地运维目录创建 `.env.k8s`，只放 Secret 值：

```ini
SECRET_KEY=replace-with-a-long-random-secret
DB_USER=pms
DB_PASSWORD=replace-with-a-strong-database-password
```

限制文件权限，并通过 kubectl 创建或更新 Secret；不要把该文件或输出的 Secret YAML 提交到 Git。Kubernetes Secret 默认只是编码，不等于加密；生产集群应限制 RBAC 并启用 etcd 静态加密或使用外部 Secret 管理服务。

```bash
chmod 600 .env.k8s
kubectl create secret generic pms-django-secret \
  --namespace pms --from-env-file=.env.k8s \
  --dry-run=client -o yaml | kubectl apply -f -
```

创建 `pms-django-config` ConfigMap，按真实域名和数据库私网地址修改，并保存为 `k8s/pms-django-config.yaml`：

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pms-django-config
  namespace: pms
data:
  DEBUG: "False"
  DB_NAME: pms
  DB_HOST: mysql.database.svc.cluster.local
  DB_PORT: "3306"
  ALLOWED_HOSTS: pms.example.com
  CSRF_TRUSTED_ORIGINS: https://pms.example.com
```

`ALLOWED_HOSTS` 与 `CSRF_TRUSTED_ORIGINS` 只有在前面“生产配置检查”所述的 settings 修改完成后才会生效。确认集群 NetworkPolicy / 数据库防火墙只允许应用命名空间访问 MySQL。
私有镜像仓库需另行配置 Kubernetes `imagePullSecrets` 或集群级凭据服务；不要把仓库密码写入 Deployment manifest。

Nginx sidecar 与 Django 在同一个 Pod，可直接经 `127.0.0.1:8000` 通信；这与 Compose 的服务 DNS 不同。创建 `deploy/nginx-k8s.conf`：

```nginx
server {
    listen 8080;
    server_name _;
    client_max_body_size 10m;

    location /static/ {
        alias /usr/share/nginx/html/static/;
        access_log off;
        expires 7d;
    }

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $http_x_forwarded_proto;
    }
}
```

创建 / 更新 Nginx 配置 ConfigMap：

```bash
kubectl create configmap pms-django-nginx \
  --namespace pms \
  --from-file=default.conf=deploy/nginx-k8s.conf \
  --dry-run=client -o yaml | kubectl apply -f -
```

#### 3. 先执行数据库迁移 Job

将镜像地址和标签替换为刚推送的版本，保存为 `k8s/migrate-job.yaml`：

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: pms-django-migrate-1-0-0
  namespace: pms
spec:
  backoffLimit: 3
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: migrate
          image: registry.example.com/pms-django:1.0.0
          command: ["python", "manage.py", "migrate", "--noinput"]
          envFrom:
            - configMapRef:
                name: pms-django-config
            - secretRef:
                name: pms-django-secret
```

```bash
kubectl apply -f k8s/pms-django-config.yaml
kubectl apply -f k8s/migrate-job.yaml
kubectl wait --for=condition=complete job/pms-django-migrate-1-0-0 \
  --namespace pms --timeout=180s
kubectl logs job/pms-django-migrate-1-0-0 --namespace pms
```

每次发布为迁移 Job 使用新的唯一名称（例如版本号或 commit SHA）；完成并检查后可按集群保留策略清理旧 Job。只有迁移 Job 成功后再更新 Web Deployment。不要在每个 Web Pod 启动命令中并行执行 `migrate`，否则滚动扩缩容时多个副本会竞争执行迁移。迁移应向后兼容；破坏性 schema 变更需单独设计发布与回滚。

#### 4. 部署 Django、Nginx sidecar、Service 与 Ingress

保存下列资源为 `k8s/pms-django-app.yaml`，替换域名、TLS Secret 名称与镜像标签：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pms-django
  namespace: pms
spec:
  replicas: 2
  selector:
    matchLabels:
      app: pms-django
  template:
    metadata:
      labels:
        app: pms-django
    spec:
      containers:
        - name: django
          image: registry.example.com/pms-django:1.0.0
          imagePullPolicy: IfNotPresent
          envFrom:
            - configMapRef:
                name: pms-django-config
            - secretRef:
                name: pms-django-secret
          env:
            - name: GUNICORN_BIND
              value: "0.0.0.0:8000"
            - name: GUNICORN_PIDFILE
              value: /tmp/gunicorn.pid
          ports:
            - name: django
              containerPort: 8000
          readinessProbe:
            httpGet:
              path: /login/
              port: django
              httpHeaders:
                - name: Host
                  value: pms.example.com
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /login/
              port: django
              httpHeaders:
                - name: Host
                  value: pms.example.com
            initialDelaySeconds: 15
            periodSeconds: 20
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: "1"
              memory: 1Gi
        - name: nginx
          image: registry.example.com/pms-django-nginx:1.0.0
          imagePullPolicy: IfNotPresent
          volumeMounts:
            - name: nginx-config
              mountPath: /etc/nginx/conf.d/default.conf
              subPath: default.conf
          ports:
            - name: http
              containerPort: 8080
          readinessProbe:
            httpGet:
              path: /login/
              port: http
              httpHeaders:
                - name: Host
                  value: pms.example.com
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /login/
              port: http
              httpHeaders:
                - name: Host
                  value: pms.example.com
            initialDelaySeconds: 15
            periodSeconds: 20
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 500m
              memory: 256Mi
      volumes:
        - name: nginx-config
          configMap:
            name: pms-django-nginx
---
apiVersion: v1
kind: Service
metadata:
  name: pms-django
  namespace: pms
spec:
  selector:
    app: pms-django
  ports:
    - name: http
      port: 80
      targetPort: http
  type: ClusterIP
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: pms-django
  namespace: pms
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - pms.example.com
      secretName: pms-django-tls
  rules:
    - host: pms.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: pms-django
                port:
                  number: 80
```

先将证书安全地创建为同命名空间内的 TLS Secret（或使用 cert-manager 自动签发）：

```bash
kubectl create secret tls pms-django-tls --namespace pms \
  --cert=/secure/path/fullchain.pem --key=/secure/path/privkey.pem
```

部署并等待滚动完成：

```bash
kubectl apply -f k8s/pms-django-app.yaml
kubectl rollout status deployment/pms-django --namespace pms
kubectl get pods,service,ingress --namespace pms
kubectl logs deployment/pms-django -c django --namespace pms
kubectl logs deployment/pms-django -c nginx --namespace pms
kubectl exec -it deployment/pms-django -c django --namespace pms -- \
  python manage.py createsuperuser
curl -I https://pms.example.com/login/
```

Ingress Controller 将 HTTPS 请求转发至 ClusterIP Service；Service 选择 Nginx sidecar 的 8080 端口；Nginx 提供 `/static/` 并把动态请求转发给同一 Pod 内的 Gunicorn。Django settings 必须信任来自受控 Ingress 的 `X-Forwarded-Proto`，且不能让客户端绕过 Ingress 直接访问 Pod。
示例 CPU / 内存 requests 与 limits 仅为起点，应根据实际负载、并发数和压测结果调整。

#### 5. 发布新版本和回滚

每次发布使用新镜像标签重新构建、推送，然后先执行与新版本匹配的迁移 Job，再更新 Deployment：

```bash
kubectl set image deployment/pms-django --namespace pms \
  django=registry.example.com/pms-django:1.0.1 \
  nginx=registry.example.com/pms-django-nginx:1.0.1
kubectl rollout status deployment/pms-django --namespace pms
```

仅在应用版本回退与数据库 schema 仍兼容时，才可使用 `kubectl rollout undo deployment/pms-django --namespace pms`。数据库恢复需按备份恢复预案执行，不能假设撤销 Deployment 会自动回滚数据库。

## 八、常见问题排查

| 问题 | 解决方法 |
|------|---------|
| `Access denied for user` | 检查 `.env` 中 DB_USER / DB_PASSWORD，并确认 MySQL 账号有 pms 库权限 |
| `Unknown database 'pms'` | 先执行第三节的建库 SQL |
| 登录后 403 / CSRF 校验失败 | 确认 `ALLOWED_HOSTS`、`CSRF_TRUSTED_ORIGINS` 和 HTTPS 代理配置已写入 settings；检查 Nginx 是否覆盖并转发 `X-Forwarded-Proto`，且浏览器使用 HTTPS |
| `cryptography` 相关报错 | `pip install cryptography`（requirements 已包含） |
| 管理员密码忘记 | `python manage.py changepassword 用户名` |
| 热部署后仍是旧代码 | 确认 `preload_app = False` 且 `systemctl reload pms` 已执行，详见 [热部署指南](hot-deployment.md) 第七节 |
| Docker / Kubernetes 返回 502 | Compose Nginx 应代理 `web:8000`；Kubernetes sidecar 应代理 `127.0.0.1:8000`，并确认 Gunicorn 绑定 `0.0.0.0:8000` |
| `/static/` 返回 404 | 重建与 Django 同版本的 Nginx 镜像，确认 `collectstatic` 执行成功且 `/app/staticfiles` 已复制到 Nginx 静态目录 |

## 九、后续扩展建议

- 任务拖拽排序（前端引入 SortableJS）
- 任务评论、附件上传
- 甘特图 / 日历视图
- 企业微信 / 钉钉登录集成
- Docker 化部署（Dockerfile + docker-compose 编排 MySQL + Web）
