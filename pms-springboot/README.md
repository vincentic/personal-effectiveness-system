# PMS Spring Boot 版

Spring Boot 3.2 + Thymeleaf + Spring Security + JdbcTemplate + MySQL。

既完整复刻了 Django 版的服务端页面，也额外提供与 Gin / FastAPI 版共用契约的 `/api/**` REST API（见 `../db/API.md`）。

## 启动

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS；Linux 用对应 JDK 17 路径
mvn spring-boot:run                                # 默认 8080
```

或：

```bash
mvn -B package -DskipTests
java -jar target/pms-springboot-0.0.1-SNAPSHOT.jar
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
| `SESSION_SECRET` | 同默认密钥 | 会话密钥（如配置） |

启动时 `spring.sql.init` 会执行 `src/main/resources/schema.sql` 幂等建表；users 表为空则自动创建默认管理员。

## 页面路由

```
/login  /logout  /
/projects/  /projects/new/  /projects/{id}/
/projects/{id}/edit/  /projects/{id}/delete/
/projects/{pid}/tasks/new/  /tasks/{id}/edit/  /tasks/{id}/delete/
/admin/users/
```

全部页面复用 Django 版 Bootstrap 5 结构与 `static/css/style.css`，未登录跳转 `/login`，项目编辑/删除仅负责人。

## API

见 `../db/API.md`。几个例子：

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

curl -s http://127.0.0.1:8080/api/dashboard -H "Authorization: Bearer $TOKEN"
curl -s http://127.0.0.1:8081/api/me -H "Authorization: Bearer $TOKEN"   # Gin 版
curl -s http://127.0.0.1:8000/api/me -H "Authorization: Bearer $TOKEN"   # FastAPI 版
```

## 测试与验证状态

- `mvn test`：✅ 通过（无编译错误，单元测试通过）
- 页面权限：表单登录 + Session + CSRF；`/api/**` 豁免 CSRF，使用 HMAC Token
- Token 三版互通：✅ 9/9 组合通过
- 跨版本 CRUD：✅ 通过
