"""Flask API configuration loaded from environment variables or a local .env file."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent


def load_dotenv() -> None:
    path = BASE_DIR / ".env"
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def env(key: str, default: str = "") -> str:
    return os.environ.get(key, default)


@dataclass(frozen=True)
class Settings:
    db_name: str = field(default_factory=lambda: env("DB_NAME", "pms"))
    db_user: str = field(default_factory=lambda: env("DB_USER", "root"))
    db_password: str = field(default_factory=lambda: env("DB_PASSWORD", ""))
    db_host: str = field(default_factory=lambda: env("DB_HOST", "127.0.0.1"))
    db_port: int = field(default_factory=lambda: int(env("DB_PORT", "3306")))
    server_port: int = field(default_factory=lambda: int(env("SERVER_PORT", "8002")))
    api_secret: str = field(default_factory=lambda: env("API_SECRET", "dev-insecure-api-secret-change-me"))
    admin_username: str = field(default_factory=lambda: env("ADMIN_USERNAME", "admin"))
    admin_password: str = field(default_factory=lambda: env("ADMIN_PASSWORD", "admin123"))
    cors_origins: str = field(default_factory=lambda: env("CORS_ORIGINS", "*"))


load_dotenv()
settings = Settings()
