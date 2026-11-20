"""BCrypt 密码与三版互通的 HMAC-SHA256 Token（详见 docs/data/api-contract.md）。"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
from typing import Any

import bcrypt

from .config import settings

TTL_SECONDS = 86400


def hash_password(password: str) -> str:
    """BCrypt 4.x 对超过 72 字节的密码抛错，这里统一截断。"""
    return bcrypt.hashpw(password.encode("utf-8")[:72], bcrypt.gensalt()).decode("ascii")


def verify_password(password: str, hashed: str) -> bool:
    try:
        return bcrypt.checkpw(password.encode("utf-8")[:72], hashed.encode("ascii"))
    except (ValueError, TypeError):
        return False


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _sign(payload: str) -> str:
    return hmac.new(
        settings.api_secret.encode("utf-8"),
        payload.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()


def issue_token(uid: int, username: str, staff: bool) -> str:
    claims = {
        "uid": uid,
        "username": username,
        "staff": bool(staff),
        "exp": int(time.time()) + TTL_SECONDS,
    }
    raw = json.dumps(claims, separators=(",", ":")).encode("utf-8")
    payload = _b64url(raw)
    return payload + "." + _sign(payload)


def verify_token(token: str) -> dict[str, Any] | None:
    """验签只覆盖收到的 payload 串；不重新序列化 claims，故字段顺序无关。"""
    if not token:
        return None
    payload, sep, signature = token.partition(".")
    if not sep or not payload or not signature:
        return None
    if not hmac.compare_digest(_sign(payload), signature):
        return None
    try:
        raw = base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4))
        claims = json.loads(raw)
        exp = claims.get("exp")
        if not isinstance(exp, int) or exp <= int(time.time()):
            return None
        return claims
    except Exception:
        return None
