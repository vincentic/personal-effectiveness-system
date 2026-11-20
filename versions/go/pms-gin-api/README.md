# PMS Gin API

Standalone Go/Gin REST API for the personal effectiveness system. The server-rendered Gin monolith lives separately in `../pms-gin`; both services implement the shared contract in `../../../docs/data/api-contract.md` and use the shared MySQL schema.

搭建步骤见[搭建指南](../../../docs/projects/go/pms-gin-api/setup.md)，编译说明见[构建过程讲解](../../../docs/projects/go/pms-gin-api/build-process-explained.md)，生产发布见[部署指南](../../../docs/projects/go/pms-gin-api/deployment.md)。

## Run

Requirements: Go 1.22+ and MySQL 8.0+.

```bash
cp .env.example .env
go run .
```

The API listens on port `8081` by default. It initializes `sql/schema.sql` and creates the configured default administrator when the `users` table is empty.

## Configuration

Set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD` for MySQL. `API_SECRET` must match other API versions to share tokens. Set `CORS_ORIGINS` to the React site's comma-separated origins when hosting React separately. `ADMIN_USERNAME` and `ADMIN_PASSWORD` apply only when initializing an empty users table.

## Shared React

The shared React application is in `../../vue/pms-vue`. From that directory, run:

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
```

The API contract, auth, and CRUD endpoints are documented in `../../../docs/data/api-contract.md`.
