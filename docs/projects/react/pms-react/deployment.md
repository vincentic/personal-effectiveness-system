# PMS React 共享前端部署文档

React 工程只负责浏览器界面，不包含 API 服务或数据库。可将它构建为独立静态站点，连接 Gin、Spring Boot、FastAPI 或 Flask API。构建细节见[构建过程讲解](build-process-explained.md)，本地安装与开发见[搭建指南](setup.md)。

## 1. 构建静态文件

```bash
cd versions/react/pms-react
pnpm install --frozen-lockfile
VITE_API_BASE_URL=https://api.example.com pnpm run build
```

构建结果默认位于 `dist/`。`VITE_API_BASE_URL` 在构建时写入浏览器资源；更改 API 地址后必须重新构建。留空时，前端调用当前站点的同源 API。所有 `VITE_*` 值都可被浏览器读取，不要放密钥或数据库凭据。

## 2. Nginx 静态站点

```nginx
server {
    listen 80;
    server_name pms.example.com;
    root /var/www/pms-react;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

将 `dist/` 内容部署到 `/var/www/pms-react/`。生产环境应配置 TLS 并使用 HTTPS。若前端与 API 使用不同 origin，应在后端 CORS allowlist 中精确加入 `https://pms.example.com`，同时允许应用所需的 `Authorization` 和 `Content-Type` 请求头。

## 3. 反向代理 API

也可以由 Nginx 同时提供前端并转发 API 请求。将 `/api/` 和 `/health` 路径代理到所选 API 服务，其他路径回退到 React 的 `index.html`。Nginx 与 API 代理示例见多语言 Docker + Kubernetes 部署指南。

## 4. 发布验证

```bash
curl -I https://pms.example.com/
```

检查静态资源请求、登录状态和浏览器 Network 面板中的 API 请求。API 返回 401 时检查登录状态与 Bearer Token；跨域错误时检查构建时的 `VITE_API_BASE_URL` 和后端 CORS 配置。

## 5. Docker 与 Kubernetes

React 可构建为 Nginx 静态镜像，通过 Ingress 将 `/` 发往 React Service、将 `/api` 发往所选 API Service。镜像与 Ingress 配置示例见多语言 Docker + Kubernetes 部署指南。
