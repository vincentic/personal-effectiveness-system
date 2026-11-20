# PMS Spring Boot Monolith

Spring Boot 3.2, Thymeleaf, Spring Security, JdbcTemplate, and MySQL. This project is the monolithic server-rendered page version with PC and mobile views; it does not expose the REST API. The standalone API service is in [`../pms-springboot-api/`](../pms-springboot-api/README.md). Both versions share the database schema.

搭建步骤见[搭建过程详解.md](搭建过程详解.md)；项目设计与实现过程见[搭建过程讲解.md](搭建过程讲解.md)。

## Run

Requirements: Java 17+ and MySQL 8.0+.

```bash
cp .env.example .env
./start.sh
```

Open <http://127.0.0.1:8080/login>. The default administrator is `admin / admin123`. Spring SQL initialization applies `src/main/resources/schema.sql` and creates the configured default administrator when the `users` table is empty.

## Configuration

Set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, and `DB_PASSWORD` for MySQL. `ADMIN_USERNAME` and `ADMIN_PASSWORD` apply only when initializing an empty users table. Page authentication uses Spring Security sessions and CSRF protection.

## Features

- Daily, weekly, and monthly checklist pages with user-scoped data.
- Responsive PC/mobile templates and a persistent view-mode selector.
- Session login, administrator account management, CSRF protection, and transactional child-record updates.

Run tests with `mvn test`. The standalone API counterpart is documented at [`../pms-springboot-api/`](../pms-springboot-api/README.md).
