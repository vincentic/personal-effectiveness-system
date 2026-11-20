"""PyMySQL 访问层：DictCursor + 通用行清洗 + 启动建表与默认管理员。"""
from __future__ import annotations

from contextlib import contextmanager
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterator

import pymysql

from .config import BASE_DIR, settings
from .security import hash_password

SCHEMA_PATH = BASE_DIR / "sql" / "schema.sql"


def connect() -> pymysql.connections.Connection:
    return pymysql.connect(
        host=settings.db_host,
        port=settings.db_port,
        user=settings.db_user,
        password=settings.db_password,
        database=settings.db_name,
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
    )



@contextmanager
def connection() -> Iterator[pymysql.connections.Connection]:
    conn = connect()
    try:
        yield conn
    finally:
        conn.close()


def clean(value: Any) -> Any:
    """把 MySQL 类型转成可直接 JSON 序列化的值。"""
    if isinstance(value, datetime):
        return value.strftime("%Y-%m-%d %H:%M:%S")
    if isinstance(value, date):
        return value.isoformat()
    if isinstance(value, Decimal):
        return int(value) if value == int(value) else float(value)
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return value


def row_clean(row: dict[str, Any] | None) -> dict[str, Any] | None:
    return {k: clean(v) for k, v in row.items()} if row else None


def query(sql: str, args: tuple | list = ()) -> list[dict[str, Any]]:
    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, args)
        return [row_clean(r) for r in (cur.fetchall() or [])]


def query_one(sql: str, args: tuple | list = ()) -> dict[str, Any] | None:
    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, args)
        return row_clean(cur.fetchone())


def execute(sql: str, args: tuple | list = ()) -> int:
    """返回 lastrowid（INSERT）或受影响行数。"""
    with connection() as conn, conn.cursor() as cur:
        cur.execute(sql, args)
        return cur.lastrowid or cur.rowcount


def statements(sql_text: str) -> list[str]:
    """按分号拆分建表脚本，忽略整行注释。"""
    parts: list[str] = []
    buf: list[str] = []
    for line in sql_text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("--"):
            continue
        buf.append(line)
        if stripped.endswith(";"):
            stmt = "\n".join(buf).strip().rstrip(";")
            if stmt:
                parts.append(stmt)
            buf = []
    if buf:
        parts.append("\n".join(buf).strip())
    return parts


def init_db() -> None:
    """幂等建表；users 表为空时创建默认管理员。"""
    sql_text = Path(SCHEMA_PATH).read_text(encoding="utf-8")
    with connection() as conn, conn.cursor() as cur:
        for stmt in statements(sql_text):
            cur.execute(stmt)
        cur.execute("SELECT COUNT(*) AS n FROM users")
        if cur.fetchone()["n"] == 0:
            cur.execute(
                "INSERT INTO users (username, password_hash, display_name, is_staff) "
                "VALUES (%s, %s, %s, %s)",
                (settings.admin_username, hash_password(settings.admin_password), "系统管理员", 1),
            )
