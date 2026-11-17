# PMS Spring Boot 版 · 个人效能系统（v2）

Spring Boot 3.2 + Thymeleaf + Spring Security + JdbcTemplate + MySQL。

服务端页面（每日执行清单 / 每周复盘 / 每月重构）与 `/api/**` REST API 共存；API 与 Gin / FastAPI 版共用
`db/API.md` 契约、`db/schema.sql` 表结构与 HMAC Token，三版同库同账号互通。

## 启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS；Linux 用对应 JDK 17 路径
mvn spring-boot:run                                # 默认 8080
```

或：

```bash
mvn -B package -DskipTests
java -jar target/pms-springboot-*.jar
```

## 配置

复制 `.env.example` 为 `.env`（或通过环境变量注入）：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `SERVER_PORT` | `8080` | 服务端口 |
| `DB_HOST` / `DB_PORT` | `127.0.0.1:3306` | MySQL 地址 |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `pms` / `root` / 空 | 数据库 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin123` | 首次启动默认管理员 |
| `API_SECRET` | `dev-insecure-api-secret-change-me` | API Token 密钥，与另两版一致 |

启动时 `spring.sql.init` 会执行 `src/main/resources/schema.sql` 幂等建表（v2：users + daily/time_blocks
+ weekly/domains/flows + monthly/health_checks）；users 表为空则自动创建默认管理员。
鉴权（HMAC Token、表单登录 + Session + CSRF）由 `support/ApiToken`、`config/SecurityConfig` 统一处理，与另两版保持一致。

## 页面路由

```
/login  /logout  /
/daily/     /daily/new/     /daily/{id}/edit/     /daily/{id}/delete/
/weekly/    /weekly/new/    /weekly/{id}/edit/    /weekly/{id}/delete/
/monthly/   /monthly/new/   /monthly/{id}/edit/   /monthly/{id}/delete/
/admin/users/
```

- 明细子表固定行数输入：每日 4 个时间盒、每周 4 领域 + 8 任务流、每月 6 健康检查项；空白行自动跳过。
- 所有清单按当前用户隔离，删除走 POST + CSRF；跨用户记录不可见。
- 复用 Bootstrap 5 与 `static/css/style.css` 的设计语言。

## API

端点与字段以 `../db/API.md` 为准，概要：

```
POST   /api/auth/login
GET    /api/me
GET    /api/dashboard
GET    /api/daily            POST   /api/daily
GET    /api/daily/{id}       PUT    /api/daily/{id}    DELETE /api/daily/{id}
GET    /api/weekly           POST   /api/weekly
GET    /api/weekly/{id}      PUT    /api/weekly/{id}   DELETE /api/weekly/{id}
GET    /api/monthly          POST   /api/monthly
GET    /api/monthly/{id}     PUT    /api/monthly/{id}  DELETE /api/monthly/{id}
GET    /api/users            POST   /api/users        （管理员）
```

除登录外均需 `Authorization: Bearer <token>`；`/api/**` 豁免 CSRF。创建返回 201、删除返回 204，
校验失败/越权/不存在分别返回 400/403/404，错误体统一 `{"detail":"..."}`。

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s http://127.0.0.1:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
curl -s -X POST http://127.0.0.1:8080/api/daily -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"plan_date":"2026-11-17","energy_level":"high","mit":"完成迁移","blocks":[{"seq":1,"block_name":"改造三版","is_done":true}]}'
```

## 测试

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvn -q test
```

单元测试覆盖读时计算的纯函数（checklist_done / block_progress / week_end / avg_score）与日期、枚举工具。
