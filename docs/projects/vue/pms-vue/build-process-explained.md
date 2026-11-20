# PMS Vue 共享前端构建过程讲解

本文说明共享 Vue 应用如何从 npm 依赖进入 TypeScript 检查、单元测试和 Vite 生产构建。安装与本地开发见[搭建指南](setup.md)，静态站点和 API 的组合部署见[部署指南](deployment.md)。

## 1. 工程边界

这是一个独立的 Vue 3 + TypeScript 单页前端，不包含业务 API、数据库或服务端页面模板。登录与日 / 周 / 月数据操作由 [`src/api.ts`](../../../../versions/vue/pms-vue/src/api.ts) 调用 REST API；同一份前端可连接 FastAPI、Gin API 或 Spring Boot API。

`package.json` 定义运行脚本和依赖，`package-lock.json` 固定完整依赖树，`vite.config.ts` 配置 Vue 插件、开发代理和构建目录。

## 2. 可重复安装依赖

需要支持当前 Vite 6 的 Node.js 与 npm。CI 和全新检出环境应使用 lockfile 安装：

```bash
cd versions/vue/pms-vue
npm ci
```

`npm ci` 按 `package-lock.json` 安装依赖，不会更新 lockfile。添加或升级依赖时才使用 `npm install`，并检查 manifest 与 lockfile 的变更。

## 3. 质量检查和生产构建

```bash
npm run typecheck
npm test
npm run build
```

- `typecheck` 使用 `vue-tsc` 按 `tsconfig.json` 检查 TypeScript 与 Vue 单文件组件，不生成 JavaScript。
- `test` 使用 Vitest 运行 `src/**/*.test.ts` 测试。
- `build` 先执行类型检查，再由 Vite 编译 Vue 组件、打包依赖、生成 HTML 和带哈希名称的静态资源。

当前 Vite 配置的默认 `outDir` 是 `versions/python/pms-fastapi/app/web`，因此默认构建直接为 FastAPI 同源托管准备文件。独立静态部署可在构建时覆盖输出目录：

```bash
npm run build -- --outDir dist
```

## 4. API 地址如何确定

开发模式中 `VITE_API_PROXY_TARGET` 指定 Vite 代理 `/api` 和 `/health` 的后端地址，默认 `http://127.0.0.1:8000`。浏览器请求经 Vite 转发，避免本地跨域。

生产模式中 `VITE_API_BASE_URL` 是构建期变量；空值代表调用当前站点的同源 API，完整 URL 则让前端请求独立 API origin。部署到独立域名时，应在构建前确定该值，并将前端站点 origin 加入 Java / Go / FastAPI 后端的 CORS allowlist。所有 `VITE_*` 值都会包含在浏览器可读取的资源中，不得设置机密。

## 5. 验收与产物

检查测试、类型和构建：

```bash
npm run typecheck
npm test
npm run build
```

默认产物位于 FastAPI 项目的 `app/web/`；使用 `--outDir dist` 时则位于 Vue 项目的 `dist/`。构建产物是可公开下载的静态文件，不包含 Node.js 服务。要提供页面，需要将这些文件部署到静态 Web 服务器，或随 FastAPI 项目一起部署；具体方式见[部署指南](deployment.md)。
