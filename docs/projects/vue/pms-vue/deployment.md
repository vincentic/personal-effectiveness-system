# PMS Vue 共享前端部署文档

本文说明将 Vue 应用作为静态站点发布，或把构建文件交给 FastAPI 同源提供。构建细节见[构建过程讲解](build-process-explained.md)，本地安装与开发见[搭建指南](setup.md)。

## 1. 部署模式选择

该 Vue 工程只负责浏览器界面，不包含 API 服务或数据库。可选择：

| 模式 | 页面由谁提供 | API origin |
|---|---|---|
| FastAPI 同源 | FastAPI 提供 Vue 构建产物和 `/api/**` | 留空，浏览器使用当前 origin |
| 独立静态站点 | Nginx / 静态托管平台提供 Vue 文件 | 构建时指定 `VITE_API_BASE_URL` |

独立静态部署可连接 Gin API、Spring Boot API、FastAPI 或 Flask API。部署前确认 API 可由浏览器访问，并配置正确的 HTTPS 和 CORS origin。

## 2. 构建静态文件

使用 lockfile 安装依赖并运行项目检查：

```bash
cd versions/vue/pms-vue
npm ci
npm run typecheck
npm test
```

### 与 FastAPI 同源部署

默认 `vite.config.ts` 将产物写入 FastAPI 的 `app/web/`。留空 `VITE_API_BASE_URL`，从仓库根目录构建：

```bash
npm --prefix versions/vue/pms-vue run build
```

将生成的 `versions/python/pms-fastapi/app/web/` 随 FastAPI 项目一起部署。不要同时用该目录存放独立站点产物，以免覆盖同源发布的文件。

### 独立静态站点

为浏览器指定 API 的公开 HTTPS 地址，并覆盖 Vite 输出目录：

```bash
cd versions/vue/pms-vue
VITE_API_BASE_URL=https://api.example.com npm run build -- --outDir dist
```

将 `dist/` 中的 HTML、JS、CSS 和图像发布到静态服务器。`VITE_API_BASE_URL` 在构建时写入前端资源；更改 API 地址后必须重新构建。`VITE_API_PROXY_TARGET` 只对 Vite 开发服务器生效，不是生产代理配置。所有 `VITE_*` 值都可被浏览器读取，不要放密钥或数据库凭据。

## 3. Nginx 静态站点示例

```nginx
server {
    listen 80;
    server_name pms.example.com;
    root /var/www/pms-vue;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

将构建产物复制到 `/var/www/pms-vue/` 后检查配置并 reload：

```bash
sudo nginx -t
sudo systemctl reload nginx
```

生产应配置 TLS 并使用 HTTPS。若前端与 API 使用不同 origin，应在后端 `CORS_ORIGINS` 中精确加入 `https://pms.example.com`，同时允许应用所需的 `Authorization` 和 `Content-Type` 请求头。Gin、Spring Boot、FastAPI 与 Flask API 的部署文档说明了对应的 CORS 配置。

## 4. 发布验证

检查静态页面、资源请求和浏览器网络面板中的 API 请求：

```bash
curl -I https://pms.example.com/
```

页面加载成功但 API 报跨域错误时，检查 `VITE_API_BASE_URL` 是否在构建时设置，以及后端 CORS 配置是否包含页面 origin。API 返回 401 时检查登录状态和 Bearer Token；重新部署静态文件不会自动更改 API 配置。

使用 FastAPI 同源模式时，API 与静态页面的联合部署细节见 FastAPI 部署指南。

## 5. Docker 与 Kubernetes

Vue 可构建为 Nginx 静态镜像，通过 Ingress 将 `/` 发往 Vue Service、将 `/api` 发往所选 API Service；也可保持 FastAPI 同源模式并将所有路径发往 FastAPI。镜像与 Ingress 配置示例见多语言 Docker + Kubernetes 部署指南。
