"""BCrypt passwords and HMAC-SHA256 tokens shared by the API implementations."""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
from typing import Any

import bcrypt

from config import settings

TTL_SECONDS = 86400


def hash_password(password: str) -> str:
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
    payload = _b64url(json.dumps(claims, separators=(",", ":")).encode("utf-8"))
    return payload + "." + _sign(payload)


def verify_token(token: str) -> dict[str, Any] | None:
    if not token:
        return None
    payload, separator, signature = token.partition(".")
    if not separator or not payload or not signature:
        return None
    if not hmac.compare_digest(_sign(payload), signature):
        return None
    try:
        raw = base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4))
        claims = json.loads(raw)
        expiration = claims.get("exp")
        if not isinstance(expiration, int) or expiration <= int(time.time()):
            return None
        return claims
    except (ValueError, TypeError, json.JSONDecodeError):
        return None
