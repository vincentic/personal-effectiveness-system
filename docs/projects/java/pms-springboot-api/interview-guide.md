# PMS Spring Boot API 面试讲解文档

本文面向项目回顾、技术面试和源码讲解。技术名称以项目实现为准，不夸大并发、分布式、缓存或微服务能力。

关联文档：

- [搭建指南](setup.md)
- [构建过程讲解](build-process-explained.md)
- [部署指南](deployment.md)
- [共享 API 契约](../../../data/api-contract.md)
- [项目源码](../../../../versions/java/pms-springboot-api/)

---

## 一、项目简介

PMS 是一个个人效能管理系统，管理每日执行、每周复盘和每月重构数据。这个项目中的 Spring Boot API 是独立的 REST 后端，负责认证、权限、业务校验、数据库访问和 JSON 响应；共享 Vue 前端负责界面和交互。

项目不是演示单个接口，而是一个前后端分离的完整链路：

```text
Vue
  -> HTTP / JSON
  -> Spring MVC Controller
  -> Repository + JdbcTemplate
  -> MySQL
```

一句话介绍：

> 我开发了一个基于 Spring Boot 3.2 和 Java 17 的个人效能 REST API，通过 Controller、JdbcTemplate 和 MySQL 实现日/周/月清单及用户管理；同时自实现跨版本兼容的 HMAC-SHA256 Token，保证该 API 与项目中的其他语言版本共用账号、数据和认证方式。

---

## 二、技术栈

| 层次 | 使用技术 | 说明 |
|---|---|---|
| 语言与运行时 | Java 17 | `pom.xml` 配置的编译和运行目标 |
| Web 框架 | Spring Boot 3.2.5、Spring MVC | REST 控制器、自动配置、依赖注入 |
| 安全 | Spring Security、BCrypt | HTTP 安全链、密码编码；业务认证另由 API Token 完成 |
| 数据访问 | Spring JDBC、JdbcTemplate | SQL 查询和参数绑定 |
| 数据库 | MySQL 8.0、InnoDB、utf8mb4 | 保存主表和明细表 |
| 数据转换 | Jackson | 请求 JSON 和响应 JSON |
| 构建 | Maven、Spring Boot Maven Plugin | 编译、测试、打包可执行 JAR |
| 前端 | Vue 3、Vite、TypeScript | 独立消费 REST API |

项目没有使用 JPA/Hibernate，而是直接使用 `JdbcTemplate` 和明确的 SQL。这样可以保证数据结构清晰，也与 Go、Python、Flask 等版本共享同一个数据库结构。

---

## 三、整体架构

项目采用分层结构：

```text
PmsApplication
  |
  |-- config/SecurityConfig
  |      +-- SecurityFilterChain、CORS、PasswordEncoder
  |
  |-- web/ApiController
  |      +-- 请求路由、参数校验、认证、权限判断、响应
  |
  |-- support/ApiToken
  |      +-- HMAC-SHA256 签发与验签
  |
  |-- repo/*
  |      +-- JdbcTemplate、SQL、事务、主表明细表装配
  |
  |-- domain/*
  |      +-- 业务数据结构
  |
  +-- resources/application.yml + schema.sql
         +-- 数据源、端口、初始化和数据库结构
```

调用关系：

```text
Vue
  -> POST /api/auth/login
  -> ApiController.login()
  -> UserRepository.findByUsername()
  -> BCryptPasswordEncoder.matches()
  -> ApiToken.issue()
  <- token + user

Vue 后续请求
  -> Authorization: Bearer <token>
  -> ApiController.require()
  -> ApiToken.verify()
  -> Repository 查询时自动限定 user_id
  <- 业务 JSON
```

---

## 四、核心功能

### 1. 用户认证

登录流程位于 `ApiController.login()`：

1. 接收 `username` 和 `password`。
2. 校验用户名或密码不能为空。
3. 根据用户名查询用户。
4. 使用 BCrypt 验证密码。
5. 验证成功后生成自定义 HMAC Token。
6. 返回 Token 和不含密码哈希的用户信息。

密码使用 BCrypt 加密，数据库只保存 `password_hash`，接口不会返回密码或哈希。

### 2. HMAC-SHA256 API Token

项目没有使用 JWT 第三方依赖，而是自实现了和其他 API 版本兼容的轻量 Token：

```text
payload = base64url(JSON(claims))
signature = hex(HMAC_SHA256(API_SECRET, payload))
token = payload + "." + signature
```

Claims 包含：

- `uid`：用户 ID
- `username`：用户名
- `staff`：是否管理员
- `exp`：过期时间，当前实现为 24 小时

验签时直接对收到的原始 payload 字符串计算签名，不重新序列化 JSON；这样不会因为 JSON 字段顺序或空格变化而导致验签失败。签名比较使用 `MessageDigest.isEqual()`，可避免普通字符串比较暴露时间差。

登录成功后，Vue 将 Token 保存在 `sessionStorage`；每次请求由前端 API 封装层添加：

```http
Authorization: Bearer <token>
```

收到 `401` 时，前端会清除本地 Token。

### 3. 用户和数据隔离

所有日、周、月清单都通过 `user_id` 与当前用户绑定。Repository 查询列表、详情和更新时都带当前用户条件，例如：

```sql
SELECT * FROM daily_plans
WHERE id = ? AND user_id = ?
```

因此跨用户访问不存在或不属于当前用户的数据时返回 `404`，而不是暴露记录或区分“存在但无权”。

管理员通过 `is_staff` 判断；只有管理员可以列出和创建用户。普通用户不能调用用户管理接口。

### 4. 每日、每周、每月清单

数据库使用主表和明细表：

| 主表 | 明细表 |
|---|---|
| `daily_plans` | `time_blocks` |
| `weekly_reviews` | `weekly_domain_reviews`、`weekly_task_flows` |
| `monthly_reviews` | `system_health_checks` |

主表保存清单主体；明细表保存时间盒、领域回顾、任务流和健康检查。外键使用 `ON DELETE CASCADE`，删除主记录时明细会自动级联删除。

更新主表时不采用逐条新增或逐条修改明细，而是整体替换：

1. 删除该主记录的旧明细。
2. 插入请求中的新明细数组。
3. 跳过空白时间盒等无效明细。

上述主表和明细写入由 `@Transactional` 包裹，保证同一个请求中的删除和插入要么一起成功，要么一起回滚。

### 5. 工作台统计

`GET /api/dashboard` 汇总当前用户的数据：

- 当日清单
- 本周清单
- 本月清单
- 最近日、周、月清单
- 最近 7 日的时间盒总数和完成数
- 日、周、月记录数量

统计逻辑在内存中从 Repository 返回的数据进行汇总，适合当前项目规模；如果未来数据量大，应考虑数据库聚合、分页和缓存。

---

## 五、数据库设计重点

数据库脚本位于 [`schema.sql`](../../../../versions/java/pms-springboot-api/src/main/resources/schema.sql)，使用 `CREATE TABLE IF NOT EXISTS`，可以重复执行而不会因表已存在报错。

Spring Boot 配置中设置：

```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

应用启动时会执行建表脚本。原因是项目需要用同一套共享表支持多个技术版本，而不是让每个语言单独维护不兼容的表结构。

核心约束包括：

- 用户名唯一。
- 每个用户每天只有一条每日清单。
- 每个用户每周只有一条周复盘。
- 每个用户每月只有一条月重构。
- 明细表按主表 ID 外键关联。

需要区分的概念：

- 唯一约束保证数据库层数据完整性。
- `@Transactional` 保证写入请求中多条 SQL 的原子性。
- Controller 校验负责快速给出友好的 400/403/404 等响应。
- Service 层在本项目中被保留得较薄；Controller 到 Repository 已足够表达当前规模。

---

## 六、一次登录和业务请求的完整流程

### 登录

```text
POST /api/auth/login
Content-Type: application/json

{"username":"admin","password":"admin123"}
```

流程：

1. SecurityFilterChain 对 `/api/**` 放行。
2. `ApiController.login()` 接收请求。
3. `UserRepository` 查询用户。
4. `BCryptPasswordEncoder.matches()` 比较密码。
5. `ApiToken.issue()` 生成 Token。
6. 响应用户信息和 Token。

### 读取数据

```text
GET /api/daily
Authorization: Bearer <token>
```

流程：

1. `require()` 从请求头解析 Bearer Token。
2. `ApiToken.verify()` 验证格式、签名和过期时间。
3. 从 Claims 中取得 `uid`。
4. 查询数据库时限定 `user_id`。
5. Repository 装配明细并计算进度字段。
6. Controller 返回 JSON。

---

## 七、Spring Boot 自动配置与启动

启动入口是 [`PmsApplication.java`](../../../../versions/java/pms-springboot-api/src/main/java/com/example/pms/PmsApplication.java)。

`@SpringBootApplication` 会触发：

- 自动配置
- 包及子包组件扫描
- 读取 `application.yml`
- 创建 Datasource 和 `JdbcTemplate`
- 初始化 Bean
- 启动内嵌 Tomcat

环境变量和配置值通过 `${...}` 注入，例如：

```yaml
url: jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/${DB_NAME:pms}
```

开发启动使用：

```bash
cd versions/java/pms-springboot-api
cp .env.example .env
./start.sh
```

`start.sh` 加载 `.env` 后执行：

```bash
mvn spring-boot:run
```

构建可执行 JAR：

```bash
mvn -B clean package
java -jar target/pms-springboot-api-1.0.0.jar
```

---

## 八、Spring Security 和 CORS 的作用边界

需要重点区分“HTTP 安全”和“业务认证”：

- Spring Security 配置 `/api/**` 放行，是为了让 Controller 自己读取和验证 Bearer Token。
- `/api/auth/login` 登录不需要 Token。
- 其他接口由 `require()` 调用 `ApiToken.verify()` 进行业务认证。
- `/api/users` 还需要 `is_staff == true`。
- CSRF 对 `/api/**` 豁免，因为客户端使用的是 Token，而不是 Cookie 和表单提交。
- 独立 Vue 的跨域来源由 `CORS_ORIGINS` 白名单控制。

这不是 Spring Security 的完整标准化认证方案，而是为了让 Spring、Gin、FastAPI 等版本使用相同的 Token 协议而采用的统一认证边界。

---

## 九、面试官可能追问

### 为什么不使用 Spring Data JPA？

项目使用 JdbcTemplate 是有意选择。原因是数据库结构由多个语言和框架共享，直接使用 SQL 更容易保持字段、约束和数据格式一致，也能让 Repository 明确管理主表和明细表。

但代价是手工映射、SQL 管理和 ResultSet 装配比 JPA 更多。未来如果项目只采用 Spring 生态并需要复杂持久化模型，可以考虑引入 JPA。

### 为什么要用自实现 HMAC Token？

项目目标是让 Spring Boot、Gin 和 Python 版本共用相同账号和认证协议。自实现的格式简单、跨语言兼容、没有依赖不同生态的 JWT 类库。

需要说明它不是 JWT。它没有标准 JWT 的完整 header、claims registry 或库生态支持。生产环境还需要考虑密钥轮换、撤销机制、刷新 Token、限流和 HTTPS。

### 为什么明细表整体替换，而不是增量更新？

表单一次提交已经包含完整明细数组。整体替换实现简单：

- 不需要为每条明细计算新增、修改、删除。
- 删除后重建能保证请求体就是最终状态。
- 配合事务可以避免部分旧数据和部分新数据混合。

代价是不能复用明细主键，且大数组写入时会产生较多 SQL。当前记录规模小，这种方案比复杂的增量同步更可控。

### 为什么跨用户访问返回 404？

因为接口没有暴露“该数据存在但无权访问”的信息，也符合项目契约。Repository 同时带 `id` 和 `user_id` 查询，因此不存在和越权访问都返回统一的 404。

### 如何保证数据一致性？

数据库通过外键、唯一约束保证结构完整性；业务写入通过 `@Transactional`保证主表和明细一起提交或回滚；Controller 通过输入校验和日期、枚举判断保证请求合法。

### 为什么启动时执行建表脚本？

共享项目需要保证表结构存在且兼容多种实现。脚本使用幂等 SQL，可以在未建表或已有表时执行。正式生产更适合引入版本化迁移工具，例如 Flyway 或 Liquibase；应停止依赖无限执行的建表脚本。

### 项目的不足是什么？

面试时可以诚实回答：

- 没有服务端分页，列表接口随数据增长会加载全部记录。
- 没有缓存和分布式并发控制。
- 当前 Dashboard 统计没有复杂数据库聚合。
- Token 没有撤销列表和刷新机制。
- 没有完整的 Service 层和领域服务。
- 缺少 Actuator/OpenAPI 等生产运维能力。
- 数据库初始化脚本适合开发共享和演示，生产应迁移到 Flyway/Liquibase。
- 密码、密钥和管理员凭据必须通过环境变量管理，不能使用默认值上线。

这些不足不影响项目作为演示和跨栈对比的目标，但也是后续演进方向。

### 这个项目体现了什么能力？

可以围绕以下点回答：

- Spring Boot 自动配置和依赖注入
- MVC 分层与 REST 接口设计
- JdbcTemplate 和事务管理
- BCrypt 和 HMAC 的安全实践
- 数据库建模及主从表设计
- 前后端数据契约和跨栈兼容
- 跨用户隔离和管理员权限
- Maven 构建和环境配置
- 启动故障定位与数据库初始化排查

---

## 十、推荐的项目讲解顺序

面试中建议按以下顺序讲，不要一上来讲每张表：

1. 项目解决什么问题。
2. 前后端如何连接。
3. 用户登录后拿到什么。
4. 每个请求如何验 Token。
5. 日/周/月数据如何建模。
6. 主表和明细表如何一起写入。
7. 数据如何按用户隔离。
8. Maven 如何编译和打包。
9. 启动环境和配置如何注入。
10. 项目不足及下一步改进。

---

## 十一、3 分钟口述稿

> 这个项目是个人效能系统的 Spring Boot REST API，主要管理日执行清单、周复盘和月重构。技术栈是 Java 17、Spring Boot 3.2、Spring MVC、Spring Security、JdbcTemplate 和 MySQL，前端是共享的 Vue 3 应用。
>
> 我把系统分成 Controller、Repository、domain、support 和配置等几层。Controller负责REST接口、参数校验、当前用户认证和权限判断；Repository使用JdbcTemplate直接操作MySQL；业务数据按日、周、月拆成主表和明细表。
>
> 登录时，系统使用BCrypt校验密码，再签发一个自实现的HMAC-SHA256 Token。Token包含用户ID、用户名、管理员标记和过期时间，前端放在Authorization请求头中。后续接口通过原始payload验签、检查过期，并在数据库查询中强制限定user_id。
>
> 对于日清单，我采用主表和time_blocks明细表。创建或更新时，主表和明细会放在同一个事务中；更新明细时先删除旧明细再插入完整的新数组，避免增量同步过于复杂。
>
> 项目还通过相同的用户表、密码格式、API契约和数据库结构，与Gin、FastAPI等版本互通。构建使用Maven打包成可执行JAR，数据源、端口、密钥和管理员账号都通过外部环境变量注入。
>
> 如果继续完善，我会引入分页、Flyway、Actuator、OpenAPI、刷新Token和撤销机制，并补充更完整的集成测试。
