# React 共享前端搭建过程详解

本文介绍如何独立安装、开发和测试 React 19 + TypeScript 前端。React 项目不自带业务 API 或数据库服务；启动前需要选择并运行 FastAPI、Gin API、Spring Boot API 或 Flask API 之一。

## 1. 环境准备

- Node.js 20 或更高版本与 pnpm 12。
- 至少一个可用的兼容 API 服务：
  - FastAPI：默认 `http://127.0.0.1:8000`
  - Spring Boot API：默认 `http://127.0.0.1:8080`
  - Gin API：默认 `http://127.0.0.1:8081`
  - Flask API：默认 `http://127.0.0.1:8002`

各后端的环境和数据库准备步骤见仓库根目录的[README.md](../../../../README.md)。

## 2. 安装依赖

```bash
cd versions/react/pms-react
cp .env.example .env.local
pnpm install
```

按需编辑 `.env.local`：

- `VITE_API_PROXY_TARGET`：Vite 开发代理后端，默认 FastAPI 的 `http://127.0.0.1:8000`。
- `VITE_API_BASE_URL`：仅在前端与 API 独立部署时设置为 API 的完整 origin；留空时使用当前来源。

不要把密钥放进 `VITE_*` 变量；Vite 会将这些值编译到浏览器端。

## 3. 启动开发服务器

先启动选定的后端，再在 React 项目目录运行：

```bash
pnpm dev
```

打开 `http://127.0.0.1:5174/`。Vite 配置会将 `/api` 和 `/health` 请求代理到 FastAPI。若使用其他 API，可临时指定代理目标：

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 pnpm dev
# 或
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 pnpm dev
# 或
VITE_API_PROXY_TARGET=http://127.0.0.1:8002 pnpm dev
```

浏览器使用所连接后端的账号登录；账号、记录和业务校验均由后端负责。React 前端的默认端口为 5173，React 使用 5174，两个开发服务器可以同时启动。

## 4. 测试、类型检查和构建

```bash
pnpm test
pnpm run typecheck
pnpm run lint
pnpm run build
```

构建会先执行 TypeScript 项目引用检查，再由 Vite 生成 `dist/` 中的静态文件。React 与 React 页面使用相同的 API 字段和认证契约，但各自独立打包。

若将 React 部署到独立静态站点，在构建前设置 `VITE_API_BASE_URL` 为 API origin，并在 API 后端配置允许的 CORS 来源。

## 5. 常见问题

- **页面能打开但请求失败**：检查后端是否启动、`VITE_API_PROXY_TARGET` 是否匹配后端端口，并查看浏览器 Network 面板。
- **跨域错误**：开发时用 Vite 代理；独立部署时设置 `VITE_API_BASE_URL` 并配置后端 CORS allowlist。
- **环境变量修改无效**：Vite 变量在启动 / 构建时读取，修改后重启开发服务器或重新构建。
- **pnpm 提示依赖构建脚本**：项目通过 `pnpm-workspace.yaml` 仅允许 Vite 所需的 esbuild 安装脚本；不要对所有依赖开放脚本权限。
- **后端无法共用登录状态**：该前端使用 Bearer Token；确保选用兼容的 REST API，而非服务端页面单体版。

## 6. 关联资料

- React 源码和测试：[src/](../../../../versions/react/pms-react/src/)
- React 共享前端：[React 项目](../../../../versions/react/pms-react/)
- API 数据结构与请求契约：API 契约
- 多语言容器部署：Docker + Kubernetes 指南
