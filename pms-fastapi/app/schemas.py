"""请求体校验模型（Pydantic v2），FastAPI 据此自动生成 /docs 的 Schema。"""
from __future__ import annotations

import string
from datetime import date
from typing import Literal

from pydantic import BaseModel, Field, field_validator

ProjectStatus = Literal["active", "paused", "done"]
TaskStatus = Literal["todo", "doing", "done"]
Priority = Literal["low", "medium", "high"]


def _iso_date(value: str | None) -> str | None:
    if value is None or value == "":
        return None
    try:
        date.fromisoformat(value)
    except ValueError as exc:
        raise ValueError("日期格式应为 YYYY-MM-DD") from exc
    return value


class LoginIn(BaseModel):
    username: str = Field(min_length=1, max_length=150)
    password: str = Field(min_length=1, max_length=72)


class UserIn(BaseModel):
    username: str = Field(min_length=1, max_length=150)
    password: str = Field(min_length=8, max_length=72)
    display_name: str = Field(default="", max_length=150)
    is_staff: bool = False


class ProjectIn(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    description: str = ""
    status: ProjectStatus = "active"
    start_date: str | None = None
    end_date: str | None = None
    member_ids: list[int] = Field(default_factory=list)

    @field_validator("start_date", "end_date", mode="before")
    @classmethod
    def check_date(cls, v):
        return _iso_date(v) if isinstance(v, str) or v is None else v


class TaskIn(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = ""
    assignee_id: int | None = None
    status: TaskStatus = "todo"
    priority: Priority = "medium"
    due_date: str | None = None

    @field_validator("due_date", mode="before")
    @classmethod
    def check_date(cls, v):
        return _iso_date(v) if isinstance(v, str) or v is None else v


string.capwords("omitempty")
