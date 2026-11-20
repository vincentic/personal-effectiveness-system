# Flask 单体版构建过程讲解

Flask 单体版把浏览器页面、Session 认证、业务表单和 MySQL 数据访问部署在一个 Python 进程中；它复用共享 v2 表结构，但不暴露 `/api/**`。

## 1. 应用启动与配置

- `config.py` 先读取系统环境变量，再从项目 `.env` 补充缺省值。
- `create_app()` 构造 Flask 应用，并设置 HttpOnly、SameSite=Lax 的 Session Cookie；HTTPS 生产环境可开启 `SESSION_COOKIE_SECURE`。
- 首个请求触发幂等 schema 初始化。数据库连接失败会直接使该请求失败，不会以成功状态掩盖错误。
- `db.py` 使用 PyMySQL DictCursor；`transaction()` 为主表与重复明细提供一次写入事务。

## 2. 浏览器请求流程

```text
浏览器表单 → Flask 路由 → Session 用户校验 + CSRF 校验
                         → Pydantic 输入校验
                         → MySQL 主表与明细事务
                         → Jinja2 模板响应
```

未登录请求会转向登录页；登录成功后 Session 只保存用户 ID，每次请求都从 `users` 表重新读取用户状态。登录、退出、创建、更新、删除和管理员操作均需有效 CSRF Token。未登录后跳转地址仅接受站内路径。

## 3. 日 / 周 / 月表单与数据隔离

- `schemas.py` 沿用 API 版的日期、月份、枚举、时间和评分校验模型，使页面提交与共享契约的输入边界一致。
- `app.py` 将清单字段、表名和明细列限定在静态配置中；用户提供的字段值均通过参数化 SQL 传递。
- 每条主记录都带有当前用户 ID；详情、编辑、删除和列表查询均按 `user_id` 约束。
- 时间盒、领域、任务流和健康检查与主记录在同一 MySQL 事务中替换；唯一键冲突返回表单提示，其他数据库异常继续向上报告。
- Jinja2 表单支持重复明细的增删；布局使用 CSS 响应式规则适配手机和桌面。

## 4. 构建和验证

Flask 是解释型应用，无需生成独立二进制。生产镜像或虚拟环境中安装 `requirements.txt` 后，用 Gunicorn 加载 `app:app`：

```bash
pip install -r requirements.txt
pip install -r requirements-dev.txt
pytest
```

需要 MySQL 才能验证真实数据库事务与共享数据；单元测试覆盖登录 CSRF、未登录重定向、表单模板渲染和用户边界。
