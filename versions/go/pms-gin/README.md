# PMS Gin Monolith

Go/Gin/MySQL server-rendered personal effectiveness app with PC and mobile pages. This project is the monolithic page version and does not expose the REST API. The standalone API service is in [`../pms-gin-api/`](../pms-gin-api/README.md); both versions share the database schema.

搭建步骤见[搭建指南](../../../docs/projects/go/pms-gin/setup.md)，编译说明见[构建过程讲解](../../../docs/projects/go/pms-gin/build-process-explained.md)，生产发布见[部署指南](../../../docs/projects/go/pms-gin/deployment.md)。

## Run

Requirements: Go 1.22+ and MySQL 8.0+.

```bash
cp .env.example .env
go run .
```

Open <http://127.0.0.1:8081/login>. The default administrator is `admin / admin123`. At startup, the app initializes `sql/schema.sql` and creates the configured default administrator when the `users` table is empty.

## Configuration

Set `DB_HOST`, `DB_PORT`, `DB_NAME`, and `DB_USER` / `DB_PASSWORD` for MySQL. `SESSION_SECRET` signs page session cookies; replace the development value in production and set `COOKIE_SECURE=true` when HTTPS is enabled. `ADMIN_USERNAME` and `ADMIN_PASSWORD` apply only when initializing an empty users table.

## Features

- Daily, weekly, and monthly checklist pages with user-scoped data.
- Responsive PC/mobile templates and a persistent view-mode selector.
- Session login, administrator account management, CSRF protection, and transactional child-record updates.

Run tests with `go test ./...`. The standalone API counterpart is documented at [`../pms-gin-api/`](../pms-gin-api/README.md).
