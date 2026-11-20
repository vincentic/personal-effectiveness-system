# 个人效能系统 — Go Gin API + React

这是面向 **Go Gin API API + React 前端** 的独立发布分支。此分支仅保留该组合、共享数据库 schema、相关搭建/构建/部署文档及对应前端 CI。

- 分支：`release/go-react`
- Release tag：`go-react-personal-effectiveness-system-v1.0.0`
- REST API：`http://127.0.0.1:8081`
- React 开发服务器：`http://127.0.0.1:5174`
- React is a separate SPA and uses the Vite development proxy to call the selected REST API.

## 快速开始

1. 准备 MySQL 8，并按后端指南设置数据库和环境变量。共享 schema 位于 [`db/schema.sql`](db/schema.sql)；后端启动流程见[项目搭建与部署文档](docs/README.md)。
2. 按[后端搭建指南](docs/projects/go/pms-gin-api/setup.md)安装依赖并启动 API。
3. 按[前端搭建指南](docs/projects/react/pms-react/setup.md)安装前端依赖。Install frontend dependencies with `pnpm install --frozen-lockfile` and start the development server with `pnpm run dev`. The Vite proxy defaults to API port 8081.

## 项目目录

- 后端：[`versions/go/pms-gin-api/`](<versions/go/pms-gin-api/README.md>)
- 前端：[`versions/react/pms-react/`](<versions/react/pms-react/README.md>)
- [构建与部署文档](docs/README.md)

后端 API 与前端遵循同一套日 / 周 / 月数据契约；生产部署时请使用 HTTPS、强随机密钥和受限的数据库账号。详见对应的搭建与部署指南。

## License

See [LICENSE](LICENSE).
