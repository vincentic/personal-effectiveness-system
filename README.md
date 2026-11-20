# 个人效能系统 — Python FastAPI + Vue

这是面向 **Python FastAPI API + Vue 前端** 的独立发布分支。此分支仅保留该组合、共享数据库 schema、相关搭建/构建/部署文档及对应前端 CI。

- 分支：`release/fastapi-vue`
- Release tag：`fastapi-vue-personal-effectiveness-system-v1.0.0`
- REST API：`http://127.0.0.1:8000`
- Vue 开发服务器：`http://127.0.0.1:5173`
- FastAPI serves the Vue app and REST API from the same origin; the Vue build writes into the FastAPI static directory.

## 快速开始

1. 准备 MySQL 8，并按后端指南设置数据库和环境变量。共享 schema 位于 [`db/schema.sql`](db/schema.sql)；后端启动流程见[项目搭建与部署文档](docs/README.md)。
2. 按[后端搭建指南](docs/projects/python/pms-fastapi/setup.md)安装依赖并启动 API。
3. 按[前端搭建指南](docs/projects/vue/pms-vue/setup.md)安装前端依赖。Build Vue from its project directory; the Vite build writes the static bundle into FastAPI `app/web/`.

## 项目目录

- 后端：[`versions/python/pms-fastapi/`](<versions/python/pms-fastapi/README.md>)
- 前端：[`versions/vue/pms-vue/`](<versions/vue/pms-vue/README.md>)
- [构建与部署文档](docs/README.md)

后端 API 与前端遵循同一套日 / 周 / 月数据契约；生产部署时请使用 HTTPS、强随机密钥和受限的数据库账号。详见对应的搭建与部署指南。

## License

See [LICENSE](LICENSE).
