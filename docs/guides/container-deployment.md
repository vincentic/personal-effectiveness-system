# 多语言 Docker + Kubernetes 部署指南

本文为仓库中的 Go、Java、Python 和共享 Vue 实现提供统一的容器化部署方式，包含 Docker 多阶段构建、Nginx HTTPS / Ingress、Kubernetes Secret、健康探针和发布验证。每次部署选择一个后端实现；不要把同端口的单体页面版与 API 版同时部署到同一主机端口。

> 示例用于说明构建与编排结构。仓库当前不提交生产镜像、真实密钥或集群配置；镜像应按组织策略固定版本或 digest，并由 CI 构建、扫描和推送。

## 1. 项目与端口映射

| 实现 | 类型 | 默认容器端口 | 推荐探针 | 页面模式 |
|---|---|---:|---|---|
| Go `pms-gin` | 服务端页面单体 | 8081 | TCP | Go 提供页面 |
| Go `pms-gin-api` | REST API | 8081 | TCP | Vue 独立部署 |
| Java `pms-springboot` | 服务端页面单体 | 8080 | HTTP `/` | Java 提供页面 |
| Java `pms-springboot-api` | REST API | 8080 | TCP | Vue 独立部署 |
| Python `pms-django` | 服务端页面 | 8000 | HTTP `/login/` | Django 提供页面 |
| Python `pms-fastapi` | Vue 同源 + REST API | 8000 | HTTP `/health` | FastAPI 提供 Vue |
| Python `pms-flask` | 服务端页面单体 | 8001 | HTTP `/health` | Flask 提供页面 |
| Python `pms-flask-api` | REST API | 8002 | HTTP `/health` | Vue 独立部署 |
| Vue `pms-vue` | 静态前端 | 8080（Nginx） | HTTP `/` | Vue 单独提供页面 |

Go API 与 Java API 当前未提供匿名健康端点，使用 Kubernetes TCP 探针；不要将受保护的 `/api/me` 401 响应误配置为 HTTP readiness probe。Django、FastAPI 与 Flask 的具体容器细节仍以各项目部署指南为准，尤其是 Django 的 `collectstatic`、静态文件和迁移步骤。

## 2. Docker 构建

### 2.1 Go 单体版与 API 版

分别在 `versions/go/pms-gin/` 或 `versions/go/pms-gin-api/` 中创建相同结构的多阶段 Dockerfile。Go 模块内含 `go:embed` 资源，构建时需复制整个项目目录：

```dockerfile
FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/pms .

FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata \
    && adduser -D -u 10001 app
COPY --from=build /out/pms /usr/local/bin/pms
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["/usr/local/bin/pms"]
```

```bash
cd versions/go/pms-gin        # API 版改为 pms-gin-api
docker build -t registry.example.com/pms-gin:1.0.0 .
```

页面版注入 `SESSION_SECRET`、`COOKIE_SECURE=true`、DB_* 与 `ADMIN_*`；API 版注入 `API_SECRET`、`CORS_ORIGINS`、DB_* 与 `ADMIN_*`。两者各自都从环境变量读取 `SERVER_PORT`，容器端口默认为 8081。

### 2.2 Java 单体版与 API 版

在 `versions/java/pms-springboot/` 或 `versions/java/pms-springboot-api/` 创建 Dockerfile：

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

```bash
cd versions/java/pms-springboot        # API 版改为 pms-springboot-api
docker build -t registry.example.com/pms-springboot:1.0.0 .
```

运行时通过环境变量传入 DB_*、`SERVER_PORT=8080` 和 `ADMIN_*`；API 版还需要 `API_SECRET` 与 `CORS_ORIGINS`。单体版使用 Session / CSRF，不需要 API Token 密钥。

### 2.3 Django 页面版

Django 的镜像需在构建阶段安装 requirements 并收集静态资源；迁移通过发布前的一次性 Job 执行，不要让每个副本并发运行迁移。项目已有 Docker Compose、Nginx sidecar 和 Kubernetes 细节，直接使用 [Django 部署指南](../projects/python/pms-django/deployment.md) 中的 Dockerfile、配置及 YAML 示例。

```bash
cd versions/python/pms-django
docker build -t registry.example.com/pms-django:1.0.0 .
```

设置 `DEBUG=False`、`SECRET_KEY`、DB_* 和允许的主机名；Django 页面认证使用 Session。

### 2.4 FastAPI + Vue 同源版

FastAPI 需要从仓库根目录构建，才能在 Node 构建阶段访问独立 Vue 工程，并将 Vue 产物写入 `pms-fastapi/app/web`。示例 Dockerfile 放在仓库根目录上下文中：

```dockerfile
FROM node:20-alpine AS frontend
WORKDIR /repo
COPY versions/vue/pms-vue/package*.json versions/vue/pms-vue/
RUN cd versions/vue/pms-vue && npm ci
COPY versions/vue/pms-vue/ versions/vue/pms-vue/
COPY versions/python/pms-fastapi/ versions/python/pms-fastapi/
RUN cd versions/vue/pms-vue && npm run build

FROM python:3.12-slim
WORKDIR /app
COPY versions/python/pms-fastapi/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY versions/python/pms-fastapi/ .
COPY --from=frontend /repo/versions/python/pms-fastapi/app/web ./app/web
EXPOSE 8000
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${SERVER_PORT:-8000}"]
```

```bash
docker build -f Dockerfile.fastapi -t registry.example.com/pms-fastapi:1.0.0 .
```

该模式下页面和 API 同源，Vue 的 `VITE_API_BASE_URL` 留空；注入 DB_*、`API_SECRET`、`ADMIN_*`。构建 context 应使用 `.dockerignore` 排除 `node_modules/`、`.git/`、`.venv/` 和 Maven `target/`。

### 2.5 Flask 单体版与 API 版

在 `versions/python/pms-flask/` 或 `versions/python/pms-flask-api/` 创建下列 Dockerfile：

```dockerfile
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --create-home --uid 10001 app
COPY --chown=app:app . .
USER 10001:10001
EXPOSE 8001
CMD ["sh", "-c", "exec gunicorn --workers 2 --bind 0.0.0.0:${SERVER_PORT:-8001} app:app"]
```

```bash
cd versions/python/pms-flask        # API 版改为 pms-flask-api
docker build -t registry.example.com/pms-flask:1.0.0 .
```

API 版将镜像的 `EXPOSE` 与默认端口改为 `8002`。单体版注入 `SECRET_KEY` 和 `SESSION_COOKIE_SECURE=true`；API 版注入 `API_SECRET`、`CORS_ORIGINS`。两版都注入 DB_* 与 ADMIN_*，且用 `/health` 探针。

### 2.6 Vue 独立静态站点

独立部署 Vue 时，在 Vite 构建阶段指定 API 公网地址，并覆盖仓库默认的 FastAPI 输出目录：

```bash
cd versions/vue/pms-vue
VITE_API_BASE_URL=https://api.example.com npm ci
VITE_API_BASE_URL=https://api.example.com npm run build -- --outDir dist
```

使用 Nginx 提供 SPA 静态资源：

```dockerfile
FROM nginx:1.27-alpine
COPY dist/ /usr/share/nginx/html/
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

Nginx `location /` 使用 `try_files $uri $uri/ /index.html;`。所有 `VITE_*` 值会进入浏览器资源；不要把密钥放进前端构建参数。

## 3. Docker 单机运行与 Nginx

生产 MySQL 建议使用独立持久化或托管实例，不要将数据库文件保存在应用容器可写层。运行后端时，将密钥通过受控 `.env` 文件或 Secret Manager 注入：

```bash
docker run -d --name pms-app \
  --env-file .env.runtime \
  -p 127.0.0.1:8002:8002 \
  registry.example.com/pms-flask-api:1.0.0
```

Nginx 可以统一提供 Vue 静态站点并把 API 路由转发到后端：

```nginx
server {
    listen 80;
    server_name pms.example.com;

    location /api/ {
        proxy_pass http://127.0.0.1:8002;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location = /health {
        proxy_pass http://127.0.0.1:8002/health;
    }

    location / {
        root /var/www/pms-vue;
        try_files $uri $uri/ /index.html;
    }
}
```

`proxy_pass` 不带 URI 后缀时保留 `/api/...` 路径。若 Nginx 直接代理服务端页面单体，则将 `location /` 指向对应的 Go、Java、Django 或 Flask 服务。部署 FastAPI 同源版时，`location /` 整体转发到 FastAPI，不要再把 `/api` 指向另一个服务。

## 4. Kubernetes 公共准备

### 4.1 集群、镜像和 Namespace

准备 Kubernetes 集群、`kubectl`、可拉取的镜像仓库，以及可从 Pod 网络访问的 MySQL。先把镜像推送至仓库，再创建命名空间：

```bash
kubectl create namespace pms
```

应用容器不需要访问公网数据库端口；MySQL 仅允许集群应用所在网络与授权账号连接。首次启动时保持应用副本数为 1，待 schema 和初始管理员完成初始化后再扩容。

### 4.2 ConfigMap 与 Secret

创建 ConfigMap 保存非敏感配置（按所选应用调整端口和变量）：

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: pms-app-config
  namespace: pms
data:
  DB_HOST: "mysql.internal"
  DB_PORT: "3306"
  DB_NAME: "pms"
  SERVER_PORT: "8002"
```

密钥通过受保护的运维终端创建；不要把明文凭据、Secret YAML 或 `.env.k8s` 提交到 Git。Secret 需要额外启用 etcd 静态加密并限制 RBAC。

```bash
umask 077
cat > .env.k8s <<'EOF'
DB_USER=pms_app
DB_PASSWORD=replace-with-a-strong-password
ADMIN_USERNAME=admin
ADMIN_PASSWORD=replace-with-a-unique-strong-password
API_SECRET=replace-with-a-long-random-secret
EOF
kubectl create secret generic pms-app-secret \
  --namespace pms --from-env-file=.env.k8s
rm .env.k8s
```

单体页面版用随机 `SECRET_KEY` 或 `SESSION_SECRET` 替代 `API_SECRET`，并按项目设置 Cookie Secure 参数。多 API 共享 Token 时，各 API 的 `API_SECRET` 必须一致。

## 5. Kubernetes 后端 Deployment + Service 示例

以下示例部署 Flask API。替换镜像地址；部署其他版本时按端口矩阵更改名称、端口与探针。对 Spring / Go API 使用 TCP 探针；Django 运行迁移 Job 后，将探针改为 `/login/`。Java 单体版可探测 `/`，允许 2xx/3xx 响应。

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: pms-flask-api
  namespace: pms
spec:
  replicas: 1
  selector:
    matchLabels:
      app: pms-flask-api
  template:
    metadata:
      labels:
        app: pms-flask-api
    spec:
      containers:
        - name: api
          image: registry.example.com/pms-flask-api:1.0.0
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8002
          envFrom:
            - configMapRef:
                name: pms-app-config
            - secretRef:
                name: pms-app-secret
          readinessProbe:
            httpGet:
              path: /health
              port: http
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /health
              port: http
            initialDelaySeconds: 20
            periodSeconds: 20
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: "1"
              memory: 512Mi
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
---
apiVersion: v1
kind: Service
metadata:
  name: pms-flask-api
  namespace: pms
spec:
  selector:
    app: pms-flask-api
  ports:
    - name: http
      port: 80
      targetPort: http
  type: ClusterIP
```

部分镜像启动时需要写临时目录或 Django 静态文件目录；如果启用 `readOnlyRootFilesystem`，显式挂载 `emptyDir` 到 `/tmp` 或应用所需路径。根据实际镜像用户 UID、资源用量和 MySQL 连接池调整 SecurityContext 与资源限制。

### 5.1 端口与探针替换

| 实现 | `containerPort` / `SERVER_PORT` | Readiness / Liveness |
|---|---:|---|
| Go 页面 / API | 8081 | `tcpSocket.port: 8081` |
| Java 页面 / API | 8080 | 页面版可 HTTP `/`；API 版用 TCP 8080 |
| Django 页面 | 8000 | HTTP `/login/`；需先执行迁移 |
| FastAPI + Vue | 8000 | HTTP `/health` |
| Flask 页面 | 8001 | HTTP `/health` |
| Flask API | 8002 | HTTP `/health` |

TCP probe 示例：

```yaml
readinessProbe:
  tcpSocket:
    port: 8081
  periodSeconds: 10
livenessProbe:
  tcpSocket:
    port: 8081
  periodSeconds: 20
```

HTTP `/health` 只适用于已提供该路由的服务。探针应与启动行为一致；若探针会触发 schema 初始化，首次部署不要并行启动多个副本。

## 6. Nginx Ingress 与 Vue 路由

集群先安装并验证 Nginx Ingress Controller，DNS 指向其外部地址，TLS 证书存入 Kubernetes Secret。Vue 独立部署时，静态 Nginx Service 使用 `pms-vue:80`；以下 Ingress 将 `/api` 与 `/health` 发往 Flask API，其他页面由 Vue SPA 提供：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: pms
  namespace: pms
  annotations:
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - pms.example.com
      secretName: pms-tls
  rules:
    - host: pms.example.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: pms-flask-api
                port:
                  number: 80
          - path: /health
            pathType: Exact
            backend:
              service:
                name: pms-flask-api
                port:
                  number: 80
          - path: /
            pathType: Prefix
            backend:
              service:
                name: pms-vue
                port:
                  number: 80
```

前端构建时若设置 `VITE_API_BASE_URL`，值应与 Ingress 对外的 API origin 一致；若 API 经由同源 `/api`，留空即可。Ingress 做同源转发时，不需要后端 CORS 通配配置。

部署其他组合时的路由规则：

- **Go / Java / Flask 服务端页面单体、Django**：只将 `/` 转发到对应页面应用 Service。
- **FastAPI 同源版**：`/`、`/api`、`/health` 均转发至 `pms-fastapi` 同一 Service。
- **独立 Vue + 任一 API**：`/api` 转发 API Service，`/` 转发 Vue 静态 Service；如暴露 `/health`，也转发 API Service。

## 7. Django 迁移与静态资源

Django 使用 ORM migration，不使用其他后端项目的 schema 初始化流程。发布新镜像后，先运行一次 Kubernetes Job 执行 `python manage.py migrate --noinput`，成功后再 rollout Deployment。静态文件通过 `collectstatic` 收集，并由 Nginx sidecar 或静态服务提供；详细 ConfigMap、PVC / emptyDir、sidecar 和回滚示例见[Django 部署指南](../projects/python/pms-django/deployment.md)。

## 8. 发布、扩容与验证

应用启动和 schema 初始化完成后再扩容：

```bash
kubectl apply -f k8s/
kubectl -n pms rollout status deployment/pms-flask-api
kubectl -n pms get pods,svc,ingress
kubectl -n pms logs deployment/pms-flask-api --tail=100
```

可用以下命令检查 Ingress 与服务状态；生产环境还应登录 Vue、调用 API，并验证 MySQL CRUD 与用户隔离：

```bash
curl -i https://pms.example.com/health
curl -i https://pms.example.com/api/me
kubectl -n pms describe ingress pms
kubectl -n pms rollout undo deployment/pms-flask-api
```

无 Token 的 `/api/me` 预期返回 401，说明 API 路由已响应；健康检查应使用 `/health` 或项目表中指定的 TCP / HTTP 探针。生产密钥使用 Kubernetes Secret 或外部 Secret 管理服务，限制 RBAC、启用 etcd 静态加密，并确保备份与轮换流程可用。
