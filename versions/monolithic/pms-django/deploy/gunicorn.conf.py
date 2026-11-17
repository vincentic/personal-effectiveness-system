"""Gunicorn 生产配置 —— 供热部署（HUP 优雅重载）使用。

用法：
    gunicorn -c deploy/gunicorn.conf.py config.wsgi:application

关键点：preload_app 必须保持 False，否则 master 会预加载旧代码，
收到 HUP 后新 worker 仍运行旧版本，热部署就失效了。
"""
import os

APP_DIR = os.environ.get("PMS_APP_DIR", "/opt/pms")

# 监听地址：由 Nginx 反向代理，不要直接暴露到公网
bind = os.environ.get("GUNICORN_BIND", "127.0.0.1:8000")
workers = int(os.environ.get("GUNICORN_WORKERS", "3"))
worker_class = "sync"

# 优雅重载：给旧 worker 处理完在途请求的时间，必须大于最长业务请求耗时
timeout = 60
graceful_timeout = int(os.environ.get("GUNICORN_GRACEFUL_TIMEOUT", "60"))
keepalive = 5

# 定期回收 worker，避免长期运行的内存膨胀；jitter 让重启错开，保证始终有 worker 可用
max_requests = 1000
max_requests_jitter = 100

# systemd 场景由 RuntimeDirectory=pms 提供 /run/pms；独立运行时写到 APP_DIR/run/
pidfile = os.environ.get("GUNICORN_PIDFILE", os.path.join(APP_DIR, "run", "gunicorn.pid"))

# 保持 False，HUP 才能重新加载应用代码
preload_app = False

# 生产用显式的 HUP 触发重载，不要用 --reload（文件监听仅适用于开发环境）
reload = False

accesslog = "-"
errorlog = "-"
loglevel = os.environ.get("GUNICORN_LOGLEVEL", "info")
capture_output = True
