# Vue 共享前端搭建过程详解

本文介绍如何独立安装、开发、测试和构建共享 Vue 3 前端。Vue 项目不自带业务数据库或 API 服务；启动前需要选择并运行 FastAPI、Gin API、Spring Boot API 或 Flask API 之一。

## 1. 环境准备

- Node.js（推荐当前 LTS）与 npm。
- 至少一个可用的兼容 API 服务：
  - FastAPI：默认 `http://127.0.0.1:8000`
  - Spring Boot API：默认 `http://127.0.0.1:8080`
  - Gin API：默认 `http://127.0.0.1:8081`
  - Flask API：默认 `http://127.0.0.1:8002`

各后端的环境和数据库准备步骤见仓库根目录的[README.md](../../../../README.md)。

## 2. 安装依赖

```bash
cd versions/vue/pms-vue
cp .env.example .env.local
npm ci
```

按需编辑 `.env.local`：

- `VITE_API_PROXY_TARGET`：Vite 开发代理后端，默认 FastAPI 的 `http://127.0.0.1:8000`。
- `VITE_API_BASE_URL`：仅在前端与 API 独立部署时设置为 API 的完整 origin；留空时使用当前来源。

不要把密钥放进 `VITE_*` 变量；Vite 会将这些值编译到浏览器端。

## 3. 启动开发服务器

先启动选定的后端，再在 Vue 项目目录运行：

```bash
npm run dev
```

打开 Vite 输出的本地地址。默认配置会将 `/api` 和 `/health` 请求代理到 FastAPI。若使用其他 API，可临时指定目标：

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
# 或
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
# 或
VITE_API_PROXY_TARGET=http://127.0.0.1:8002 npm run dev
```

浏览器登录使用所连接后端的账号；账号、记录和业务校验均由后端负责。

## 4. 单测、类型检查和生产构建

```bash
npm test
npm run typecheck
npm run build
```

`npm run build` 会先做 TypeScript / Vue 类型检查，再生成生产静态文件。当前 `vite.config.ts` 将产物输出到 `versions/python/pms-fastapi/app/web`，便于 FastAPI 同源托管。该构建会清理并重建输出目录。

若将 Vue 部署到独立静态站点，在构建前设置 `VITE_API_BASE_URL` 为 API origin，并在 API 后端配置允许的 CORS 来源。

## 5. 常见问题

- **页面能打开但请求失败**：检查后端是否启动、`VITE_API_PROXY_TARGET` 是否匹配后端端口，并查看浏览器 Network 面板。
- **跨域错误**：开发时用 Vite 代理；独立部署时正确设置 `VITE_API_BASE_URL` 和后端 CORS 配置。
- **环境变量修改无效**：`.env.local` 中的 Vite 变量在启动 / 构建时读取，修改后重启开发服务器或重新构建。
- **缺少构建命令或依赖**：确认在本目录运行了 `npm ci`，并使用受支持的 Node.js LTS。
- **后端无法共用登录状态**：该前端使用 Bearer Token；确保选用兼容的 REST API，而非 Go、Java 或 Django 页面单体版。

## 6. 关联资料

- 前端代码和测试：[src/](../../../../versions/vue/pms-vue/src/)
- API 数据结构与请求契约：API 契约
- FastAPI 同源托管方式：[FastAPI 搭建详解](../../python/pms-fastapi/setup.md)
- Go API：Gin API 搭建详解
- Java API：Spring Boot API 搭建详解
- Flask API：Flask API 搭建指南
