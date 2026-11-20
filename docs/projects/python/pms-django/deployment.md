# 项目管理系统部署文档

基于 **Python 3.10+ / Django 4.2 / MySQL 8.0** 的简单项目管理系统。构建与迁移过程详见[构建过程讲解](build-process-explained.md)。

---

## 一、系统功能

- 用户登录 / 退出（Django 内置认证）
- 工作台：项目、任务统计概览，我的项目与我的待办
- 项目管理：新建、编辑、删除、按状态/名称筛选，成员管理，任务进度条
- 任务管理：三列看板（待处理 / 进行中 / 已完成），指派执行人、优先级、截止日期
- Django Admin 后台管理

## 二、目录结构

```
versions/python/pms-django/ # 仓库内按服务端语言归档的 Django 版目录
├── manage.py                 # Django 管理入口
├── requirements.txt          # Python 依赖
├── .env.example              # 环境变量示例（复制为 .env 使用）
├── README.md                 # 项目说明（另有 zh-CN / zh-TW / en 多语言版）
├── deploy/                   # 生产部署与热部署配置
│   ├── gunicorn.conf.py      # Gunicorn 生产配置（优雅重载参数）
│   ├── pms.service           # systemd 单元（含 ExecReload）
│   ├── hot-deploy.sh         # 生产一键热部署脚本
│   └── local-hot.sh          # 本地命令行热部署 / watch
├── config/                   # 项目配置
│   ├── __init__.py           # PyMySQL → MySQLdb 兼容层
│   ├── settings.py           # 全局配置（读取 .env）
│   ├── urls.py               # 根路由
│   └── wsgi.py
├── projects/                 # 核心应用
│   ├── models.py             # Project / Task 模型
│   ├── views.py              # 视图（登录、工作台、项目、任务）
│   ├── forms.py, admin.py, urls.py
│   └── migrations/
├── templates/                # 页面模板（Bootstrap 5）
└── static/css/style.css      # 自定义样式
```

## 三、环境准备

| 软件 | 版本要求 |
|------|---------|
| Python | 3.10+ |
| MySQL | 8.0+（5.7 亦可） |
| Nginx（生产可选） | 1.18+ |

### 1. 安装 MySQL

```bash
# macOS
brew install mysql && brew services start mysql

# Ubuntu / Debian
sudo apt update && sudo apt install -y mysql-server
sudo systemctl enable --now mysql
```

### 2. 创建数据库

```bash
mysql -u root -p
```

```sql
CREATE DATABASE pms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- 建议创建专用账号（把 your-password 换成自己的密码）
CREATE USER 'pms'@'%' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON pms.* TO 'pms'@'%';
FLUSH PRIVILEGES;
```

## 四、部署步骤

### 1. 获取代码并创建虚拟环境

```bash
cd pms/versions/python/pms-django
python3 -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### 2. 配置环境变量

```bash
cp .env.example .env
vim .env
```

生产环境务必修改：

```ini
DEBUG=False
SECRET_KEY=换成一段随机长字符串
DB_NAME=pms
DB_USER=pms
DB_PASSWORD=your-password
DB_HOST=127.0.0.1
DB_PORT=3306
```

### 3. 初始化数据库并创建管理员

```bash
python manage.py makemigrations projects
python manage.py migrate
python manage.py createsuperuser   # 按提示输入用户名/邮箱/密码
```

### 4. 本地开发运行

```bash
python manage.py runserver 0.0.0.0:8000
```

浏览器访问 http://127.0.0.1:8000 ，登录后即可使用。
后台地址：http://127.0.0.1:8000/admin/

## 五、生产环境部署（Linux + Gunicorn + Nginx）

### 1. 收集静态文件

```bash
python manage.py collectstatic --noinput
```

### 2. 用 Gunicorn 启动服务

推荐直接使用仓库中的生产配置（含优雅重载参数，详见 [热部署指南](hot-deployment.md)）：

```bash
pip install gunicorn                 # requirements.txt 已包含
gunicorn -c deploy/gunicorn.conf.py config.wsgi:application
```

建议写成 systemd 服务 `/etc/systemd/system/pms.service`，内容可直接复制 `deploy/pms.service`：

```ini
[Unit]
Description=PMS Django Service (Gunicorn)
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=www-data
Group=www-data
WorkingDirectory=/opt/pms
Environment="PATH=/opt/pms/.venv/bin"
Environment="DJANGO_SETTINGS_MODULE=config.settings"
Environment="GUNICORN_PIDFILE=/run/pms/gunicorn.pid"
EnvironmentFile=-/opt/pms/.env

RuntimeDirectory=pms
RuntimeDirectoryMode=0755
PIDFile=/run/pms/gunicorn.pid

ExecStart=/opt/pms/.venv/bin/gunicorn -c /opt/pms/deploy/gunicorn.conf.py config.wsgi:application
ExecReload=/bin/kill -HUP $MAINPID

KillMode=mixed
KillSignal=TERM
TimeoutStopSec=75
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
```

`ExecReload` 是热部署的关键：`systemctl reload pms` 会向 master 发送 HUP，新 worker 加载新代码，旧 worker 处理完在途请求后退出，服务不中断。

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now pms
```

### 3. Nginx 反向代理

`/etc/nginx/conf.d/pms.conf`：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    client_max_body_size 10M;

    location /static/ {
        alias /opt/pms/staticfiles/;
    }

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

### 4. 热部署（不停机发布）

日常更新代码不要用 `systemctl restart`（会中断连接），改用 Gunicorn 的 HUP 优雅重载：

```bash
cd /opt/pms
./deploy/hot-deploy.sh --dry-run    # 先演练，只打印命令
./deploy/hot-deploy.sh              # 正式发布
```

脚本依次完成代码拉取、依赖安装、`check` / `migrate` / `collectstatic`、`systemctl reload pms` 与 `/login/` 健康检查；任一步失败即中止且不会触发重载。新旧 worker 会短暂共存，Nginx 上游端口不变，因此无需 reload Nginx。

> 关键约束：`deploy/gunicorn.conf.py` 中必须保持 `preload_app = False`；数据库迁移需向后兼容；含破坏性迁移时改用蓝绿部署。完整原理、参数、回滚与排错见 [热部署指南](hot-deployment.md)。

## 六、常见问题排查

| 问题 | 解决方法 |
|------|---------|
| `Access denied for user` | 检查 `.env` 中 DB_USER / DB_PASSWORD，并确认 MySQL 账号有 pms 库权限 |
| `Unknown database 'pms'` | 先执行第三节的建库 SQL |
| `django.core.cache` 或登录后 403 | DEBUG=False 时需正确配置 `ALLOWED_HOSTS`（settings 中已默认 `["*"]`，生产建议改为具体域名） |
| `cryptography` 相关报错 | `pip install cryptography`（requirements 已包含） |
| 管理员密码忘记 | `python manage.py changepassword 用户名` |
| 热部署后仍是旧代码 | 确认 `preload_app = False` 且 `systemctl reload pms` 已执行，详见 [热部署指南](hot-deployment.md) 第七节 |

## 七、后续扩展建议

- 任务拖拽排序（前端引入 SortableJS）
- 任务评论、附件上传
- 甘特图 / 日历视图
- 企业微信 / 钉钉登录集成
- Docker 化部署（Dockerfile + docker-compose 编排 MySQL + Web）
