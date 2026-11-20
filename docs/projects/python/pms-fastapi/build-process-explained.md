# PMS FastAPI + Vue 构建过程讲解

本文说明 Python API 服务和独立 Vue 前端如何分别准备、构建并组合为同源应用。环境准备见[搭建指南](setup.md)，生产发布见[部署指南](deployment.md)；请求字段和认证约定见API 契约。

## 1. 两个工程、两个构建阶段

后端在 [`versions/python/pms-fastapi/`](../../../../versions/python/pms-fastapi/)，前端在 [`versions/vue/pms-vue/`](../../../../versions/vue/pms-vue/)。FastAPI 使用 Python 依赖和 Uvicorn；Vue 使用 npm、TypeScript 和 Vite。二者通过 HTTP/JSON 和 Bearer Token 协作，不共享源代码目录。

```text
Vue 源码 --Vite 构建--> app/web 静态文件
FastAPI 源码 + app/web + MySQL 配置 --> 同源页面与 REST API
```

FastAPI 在启动时执行数据库表初始化并注册 `/api/**` 路由。构建后的 `app/web/index.html` 与 assets 由 FastAPI 提供，因此同源部署不需为浏览器单独配置跨域。

## 2. 构建 Python 后端环境

```bash
cd versions/python/pms-fastapi
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
cp .env.example .env
```

Python 依赖由 `requirements.txt` 声明。该项目不产生独立 wheel 或本地编译二进制；部署工件是 Python 源码、依赖清单、SQL 初始化脚本和运行环境配置。生产部署可在目标服务器建立虚拟环境并安装依赖。

## 3. 构建 Vue 前端到 FastAPI

从仓库根目录安装锁定的 npm 依赖并构建：

```bash
npm --prefix versions/vue/pms-vue ci
npm --prefix versions/vue/pms-vue run typecheck
npm --prefix versions/vue/pms-vue test
npm --prefix versions/vue/pms-vue run build
```

`npm run build` 先运行 `vue-tsc --noEmit`，通过后再调用 Vite。[`vite.config.ts`](../../../../versions/vue/pms-vue/vite.config.ts) 将 `build.outDir` 指向 `versions/python/pms-fastapi/app/web`；该输出包含 `index.html` 和带哈希的静态 assets。每次构建会清空旧输出目录后重新生成，因此只有完成构建的版本才应打包发布。

独立开发时 `VITE_API_PROXY_TARGET` 只控制 Vite 开发服务器的 `/api`、`/health` 代理；生产构建由 `VITE_API_BASE_URL` 决定浏览器请求 API 的 origin。变量以 `VITE_` 开头会进入浏览器 bundle，不能放 Token 签名密钥或数据库密码。

## 4. 组合发布与验证

部署 FastAPI 同源应用时，先完成上述 Vue 构建，再将包含 `app/web/` 的 FastAPI 项目部署到服务器。服务器侧建立虚拟环境、安装 `requirements.txt`、设置 `.env`，再由 Uvicorn 或生产进程管理器启动 `app.main:app`。

```bash
cd versions/python/pms-fastapi
python -m compileall app
```

启动后检查 `/health`、`/`、`/docs` 和登录端点。若访问 `/` 时没有检测到 `app/web/index.html`，应重新执行前端构建并确认发布包中包含 `app/web`，而不是把该错误当作数据库故障。

数据库初始化脚本使用幂等建表语句，不负责创建 MySQL 数据库，也不会自动演进已存在表的结构；表结构升级应按单独的迁移计划执行。生产部署、systemd 与 Nginx 配置见[部署指南](deployment.md)。
