# Django 页面版搭建过程详解

本文说明如何本地搭建 Django 服务端页面应用。Django 使用自己的 Session 认证和 ORM 迁移，不是供共享 Vue 调用的 REST API。

## 1. 环境准备

- Python 3.10 或更高版本。
- MySQL 8.0 或更高版本（兼容 MySQL 5.7）。
- pip 与 venv（Python 标准库提供）。

启动 MySQL 并创建数据库：

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

Django 通过 `manage.py migrate` 应用项目迁移来创建或更新表；不要用其他版本的 `schema.sql` 替代 Django 迁移。

## 2. 创建 Python 环境并安装依赖

在仓库根目录执行：

```bash
cd versions/python/pms-django
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
```

Windows PowerShell 激活虚拟环境的命令为：

```powershell
.venv\Scripts\Activate.ps1
```

## 3. 配置数据库并初始化

```bash
cp .env.example .env
```

编辑 `.env` 中的 `DB_NAME`、`DB_USER`、`DB_PASSWORD`、`DB_HOST`、`DB_PORT`。开发用 `SECRET_KEY` 仅为占位值；生产环境应设置新的随机密钥，并将 `DEBUG=False`。

迁移数据库并创建管理员：

```bash
python manage.py migrate
python manage.py createsuperuser
```

Django 不会自动创建默认管理员；请按提示交互式输入管理员用户名和密码。

## 4. 启动并使用

```bash
python manage.py runserver 127.0.0.1:8000
```

打开 <http://127.0.0.1:8000/>。Django Admin 位于 <http://127.0.0.1:8000/admin/>。这是服务端模板页面应用，不提供 `/api/**` REST API。

## 5. 验证与静态资源

```bash
python manage.py check
python manage.py test
```

开发模式由 Django 提供静态文件。生产部署前执行：

```bash
python manage.py collectstatic --noinput
```

生产环境建议使用 Gunicorn 和 Nginx；具体服务配置、静态文件托管与排错见[部署指南](deployment.md)。

## 6. 常见问题

- **`Access denied` 或连接失败**：确认 MySQL 可连接、数据库已创建，并核对 `.env` 中的 DB_* 配置与账号权限。
- **模板或功能与其他版本不同**：Django 是独立的服务端页面实现，使用自己的路由、表单、Session 与 ORM；不要把 API 版的运行方式套用到此项目。
- **找不到 `createsuperuser` 命令**：确认虚拟环境已激活且依赖已安装，并在含 `manage.py` 的目录执行命令。
- **开发服务器仅可本机访问**：如需局域网临时测试，可运行 `python manage.py runserver 0.0.0.0:8000`；不要将开发服务器直接用于生产。

## 7. 关联资料

- 项目快速开始：[README.md](../../../../versions/python/pms-django/README.md)
- 生产部署：[部署指南](deployment.md)
- 不停机发布：[热部署指南](hot-deployment.md)
- 共享业务数据与 API 契约：[数据模型设计](../../../data/schema-design.md)、[API 契约](../../../data/api-contract.md)
