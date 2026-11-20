# PMS Spring Boot API

Standalone Java/Spring Boot REST API for the personal effectiveness system. The server-rendered Spring Boot monolith lives separately in `../pms-springboot`; both services implement the shared contract in `../../../docs/data/api-contract.md` and use the shared MySQL schema.

搭建步骤见[搭建指南](../../../docs/projects/java/pms-springboot-api/setup.md)，构建说明见[构建过程讲解](../../../docs/projects/java/pms-springboot-api/build-process-explained.md)，生产发布见[部署指南](../../../docs/projects/java/pms-springboot-api/deployment.md)。

## Run

Requirements: Java 17+ and MySQL 8.0+.

```bash
cp .env.example .env
./start.sh
```

The API listens on port `8080` by default. Spring SQL initialization applies `src/main/resources/schema.sql`; the configured default administrator is created when the `users` table is empty.

## Configuration

Set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD` for MySQL. `API_SECRET` must match other API versions to share tokens. Set `CORS_ORIGINS` to the Vue site's comma-separated origins when hosting Vue separately. `ADMIN_USERNAME` and `ADMIN_PASSWORD` apply only when initializing an empty users table.

## Shared Vue

The shared Vue application is in `../../vue/pms-vue`. From that directory, run:

```bash
VITE_API_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
```

The API contract, auth, and CRUD endpoints are documented in `../../../docs/data/api-contract.md`.
