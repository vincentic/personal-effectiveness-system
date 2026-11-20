# PMS Django 构建过程讲解

本文解释 Django 页面项目从 Python 依赖安装、模型迁移到生产静态文件收集的流程。环境和本地启动见[搭建指南](setup.md)，Gunicorn 与 Nginx 发布见[部署指南](deployment.md)，不停机更新见[热部署指南](hot-deployment.md)。

## 1. 项目结构与运行入口

[`requirements.txt`](../../../../versions/python/pms-django/requirements.txt) 固定 Django、PyMySQL、cryptography 和 Gunicorn 等依赖。`manage.py` 是开发与管理命令入口；生产 WSGI 应用是 `config.wsgi:application`。

```text
浏览器 → Django URL / middleware → projects.views / forms / models
       → MySQL
       └→ Django Templates + static/css
```

该版本是服务端渲染的 Django 单体页面，不生成独立的前端 bundle，也没有 `npm build` 阶段。Python 源码在部署时由解释器加载；生产工件由项目源码、安装后的 Python 依赖、迁移文件和收集后的静态文件组成。

## 2. 创建环境与安装依赖

```bash
cd versions/python/pms-django
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
cp .env.example .env
```

虚拟环境将此项目依赖与系统 Python 隔离。`config/__init__.py` 注册 PyMySQL 作为 MySQLdb 兼容驱动，因此无需在构建机编译 `mysqlclient`。系统环境变量优先于 `.env`；发布前设置强 `SECRET_KEY`、关闭 `DEBUG` 并正确配置数据库。

## 3. 模型迁移和静态文件阶段

项目已经包含 `projects/migrations/0001_initial.py`。开发时改变模型后生成新的迁移并审阅迁移文件；部署时只应用已提交的迁移，不在服务器临时生成迁移。

```bash
python manage.py check
python manage.py makemigrations projects   # 仅模型改变时执行
python manage.py migrate
python manage.py test
python manage.py collectstatic --noinput
```

- `check` 验证 Django 配置和应用注册。
- `migrate` 按迁移历史创建或升级数据库对象。
- `test` 执行 Django 测试发现器。
- `collectstatic` 汇总应用静态文件到 `STATIC_ROOT`（当前为项目的 `staticfiles/`），供 Nginx 等静态服务器提供。

`makemigrations` 属于开发者生成迁移的工作流，不应作为每次生产发布的自动步骤。数据库迁移应先确认兼容性与备份策略。

## 4. 运行与发布产物

开发时运行：

```bash
python manage.py runserver 127.0.0.1:8000
```

生产由 Gunicorn 导入 WSGI 入口：

```bash
gunicorn -c deploy/gunicorn.conf.py config.wsgi:application
```

Django 本身没有把项目打包成单一可执行文件的阶段。发布应复制应用代码与迁移，安装锁定依赖，注入运行配置，执行 `migrate` 和 `collectstatic`，然后启动或优雅重载 Gunicorn。不要复制开发机的 `.venv`；在目标平台创建环境能避免 Python ABI 和操作系统差异。

## 5. 验收

```bash
python manage.py check
python manage.py test
curl -I http://127.0.0.1:8000/login/
```

生产健康检查还应确认登录页面、MySQL 连接和 `/static/` 资源都能通过实际反向代理访问。systemd、Nginx 和热部署脚本见对应[部署文档](deployment.md)与[不停机发布文档](hot-deployment.md)。
