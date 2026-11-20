# PMS Spring Boot API 构建过程讲解

本文说明 REST API 项目如何由 Maven 依赖解析、Java 编译、测试和 Spring Boot 可执行 JAR 打包。项目搭建步骤见[搭建指南](setup.md)，Linux 生产发布见[部署指南](deployment.md)；API 字段与行为以共享契约为准。

## 1. 工程与依赖边界

[`pom.xml`](../../../../versions/java/pms-springboot-api/pom.xml) 使用 Spring Boot 3.2.5 parent 管理依赖版本，Java 目标版本为 17。项目依赖 Spring MVC、Spring Security、Validation、JdbcTemplate 和 MySQL Connector；Spring Boot Test 用于测试。该 API 项目不包含 React 源码或服务端页面模板。

```text
React / API 客户端 → Spring MVC Controller → Token / 输入校验
                 → Repository + JdbcTemplate → MySQL
```

`PmsApplication` 是组件扫描和自动配置入口。Controller、Repository、数据对象、Token 支持类和初始化逻辑在该启动类的包层级下注册。数据库结构从 classpath 的 `schema.sql` 初始化，初始管理员只在用户表为空时创建。

## 2. Maven 构建阶段

在项目目录运行：

```bash
cd versions/java/pms-springboot-api
mvn -B clean package
```

Maven 依次解析 `pom.xml` 中的依赖、编译 `src/main/java`、复制资源、编译并运行测试。`spring-boot-maven-plugin` 在 package 阶段将应用和运行依赖重新打包为可执行 JAR。开发时也可单独运行 `mvn -B test` 快速验证。

主要目录：

| 路径 | 构建用途 |
|---|---|
| `src/main/java/` | Controller、Repository、领域对象及应用配置 |
| `src/main/resources/application.yml` | 运行时数据源、端口、日志与初始化配置 |
| `src/main/resources/schema.sql` | 随应用打包并在启动时执行的幂等建表脚本 |
| `src/test/java/` | Maven 测试源码 |
| `target/` | Maven 输出目录，不应手工编辑 |

构建产物为 `target/pms-springboot-api-1.0.0.jar`。API 运行时不要求目标机器上存在 Maven，但需要兼容的 JDK 17+ 和可访问的 MySQL。

## 3. 从源码启动与从 JAR 启动

[`start.sh`](../../../../versions/java/pms-springboot-api/start.sh) 在源码目录加载 `.env`，然后执行 `mvn spring-boot:run`，适合开发调试。直接执行 `java -jar` 时，Spring Boot 不会自动加载 `.env`；生产应通过 systemd `EnvironmentFile`、容器环境变量或平台密钥服务注入配置。

```bash
cp .env.example .env
./start.sh

# 生产风格的本地运行示例
SERVER_PORT=8080 API_SECRET='替换为安全随机值' \
  java -jar target/pms-springboot-api-1.0.0.jar
```

配置重点是 `DB_*`、`SERVER_PORT`、`API_SECRET`、`CORS_ORIGINS` 和首次创建管理员用的 `ADMIN_*`。共享 React 独立托管时，将其站点 origin 加入 CORS 白名单；跨 API 版本共用 Token 时，三端 `API_SECRET` 必须一致。不要将密钥打包到 JAR 或前端。

## 4. 验证构建

```bash
mvn -B test
```

当前项目未配置 Actuator 健康端点或 OpenAPI 插件。可用无 Token 的受保护接口检查服务是否响应：

```bash
curl -i http://127.0.0.1:8080/api/me
```

预期返回 `401`；再调用 `POST /api/auth/login` 验证数据库连接与认证。`target/` 是可重建输出，不应作为源代码提交。发布时复制 JAR 和外部运行配置即可；Nginx、systemd 与升级流程见[部署指南](deployment.md)。
