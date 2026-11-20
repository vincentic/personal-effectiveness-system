"""
PyMySQL 兼容层：将 PyMySQL 伪装为 MySQLdb，
这样无需编译 mysqlclient 即可连接 MySQL。
放在包 __init__.py 中保证 Django 启动时最先执行。
"""
import pymysql

pymysql.install_as_MySQLdb()
pymysql.version_info = (1, 4, 6, "final", 0)  # 伪装版本，绕过 Django 版本检查
