# PMS React 共享前端构建过程讲解

本文说明 React 应用如何从 pnpm 依赖进入 TypeScript 检查、单元测试和 Vite 生产构建。安装与本地开发见[搭建指南](setup.md)，静态站点发布见[部署指南](deployment.md)。

## 1. 工程边界

这是一个独立的 React 19 + TypeScript 单页前端，不包含业务 API、数据库或服务端页面模板。登录与日 / 周 / 月数据操作由 [`src/api.ts`](../../../../versions/react/pms-react/src/api.ts) 调用共享 REST API。React 与 React 的输入字段和 API 路径遵循同一份契约。

`package.json` 定义运行脚本和依赖，`pnpm-lock.yaml` 锁定依赖解析结果，`vite.config.ts` 配置 React 插件、开发代理和默认开发端口 5174。

## 2. 可重复安装依赖

全新检出环境应使用 pnpm lockfile 安装依赖：

```bash
cd versions/react/pms-react
pnpm install --frozen-lockfile
```

`pnpm-workspace.yaml` 只允许 `esbuild` 执行安装脚本，以支持 Vite 编译；不应对所有依赖开放生命周期脚本。

## 3. 质量检查和生产构建

```bash
pnpm run typecheck
pnpm test
pnpm run lint
pnpm run build
```

- `typecheck` 使用 TypeScript project references 检查浏览器应用和 Vite 配置，不生成 JavaScript。
- `test` 使用 Vitest 检查 REST API origin、JSON 登录、Bearer Token、401 清理和 204 删除响应。
- `lint` 使用 ESLint 检查 TypeScript、React Hooks 与组件刷新约束。
- `build` 先执行 TypeScript 项目检查，再由 Vite 编译 JSX、打包依赖并生成带哈希名称的静态资源。

构建产物默认位于 `dist/`，不会写入 Python 项目或 React 目录；也不会自动复制到 FastAPI 的静态目录。

## 4. API 地址如何确定

开发模式中 `VITE_API_PROXY_TARGET` 指定 Vite 代理 `/api` 和 `/health` 的后端地址，默认 `http://127.0.0.1:8000`。浏览器请求经 Vite 转发，避免本地跨域。

生产模式中 `VITE_API_BASE_URL` 是构建期变量；空值代表调用当前站点的同源 API，完整 URL 则让前端请求独立 API origin。部署到独立域名时，应在构建前确定该值，并将前端站点 origin 加入后端 CORS allowlist。所有 `VITE_*` 值都会包含在浏览器可读取的资源中，不得设置机密。

## 5. 验收与产物

```bash
pnpm run typecheck
pnpm test
pnpm run lint
pnpm run build
```

`dist/` 是可公开下载的静态文件，不包含 Node.js 服务。将其部署到 Nginx 或静态 Web 托管平台即可；具体方式见[部署指南](deployment.md)。
