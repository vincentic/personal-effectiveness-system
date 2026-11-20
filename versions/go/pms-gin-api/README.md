# PMS Gin API

Standalone Go/Gin REST API for the personal effectiveness system. The server-rendered Gin monolith lives separately in `../pms-gin`; both services implement the shared contract in `../../../db/API.md` and use the shared MySQL schema.

## Run

Requirements: Go 1.22+ and MySQL 8.0+.

```bash
cp .env.example .env
go run .
```

The API listens on port `8081` by default. It initializes `sql/schema.sql` and creates the configured default administrator when the `users` table is empty.

## Configuration

Set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD` for MySQL. `API_SECRET` must match other API versions to share tokens. Set `CORS_ORIGINS` to the Vue site's comma-separated origins when hosting Vue separately. `ADMIN_USERNAME` and `ADMIN_PASSWORD` apply only when initializing an empty users table.

## Shared Vue

The shared Vue application is in `../../python/pms-fastapi/frontend`. From that directory, run:

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev
```

The API contract, auth, and CRUD endpoints are documented in `../../../db/API.md`.
