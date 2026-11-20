"""Pydantic request models shared by the Flask form workflows."""
from __future__ import annotations

from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field, field_validator

EnergyLevel = Literal["low", "medium", "high"]
DomainName = Literal["work", "study", "body", "relations"]
DomainStatus = Literal["smooth", "blocked", "paused"]
Bucket = Literal["todo", "doing", "done", "remove"]
WorthChoice = Literal["yes", "adjust", "stop"]
HealthModule = Literal[
    "direction", "taskload", "blockquality", "timebox", "sleepenergy", "reviewquality"
]


def _iso_date(value: str | None) -> str | None:
    if value is None or value == "":
        return None
    try:
        date.fromisoformat(value)
    except (ValueError, TypeError) as exc:
        raise ValueError("日期格式应为 YYYY-MM-DD") from exc
    return value


def _iso_month(value: str | None) -> str | None:
    if value is None or value == "":
        return None
    if not isinstance(value, str) or len(value) != 7 or value[4] != "-":
        raise ValueError("指标格式应为 YYYY-MM")
    try:
        datetime.strptime(value, "%Y-%m")
    except ValueError as exc:
        raise ValueError("指标格式应为 YYYY-MM") from exc
    return value


def _hhmm(value: str | None) -> str | None:
    if value is None or value == "":
        return None
    if not isinstance(value, str):
        raise ValueError("时间格式应为 HH:MM")
    try:
        datetime.strptime(value, "%H:%M")
    except ValueError as exc:
        raise ValueError("时间格式应为 HH:MM") from exc
    return value


def _score(value: object) -> int | None:
    if value is None or value == "":
        return None
    if isinstance(value, bool) or (isinstance(value, float) and not value.is_integer()):
        raise ValueError("自评分应为 1-5")
    try:
        number = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError("自评分应为 1-5") from exc
    if not 1 <= number <= 5:
        raise ValueError("自评分应为 1-5")
    return number


class LoginIn(BaseModel):
    username: str = Field(min_length=1, max_length=150)
    password: str = Field(min_length=1, max_length=72)


class UserIn(BaseModel):
    username: str = Field(min_length=1, max_length=150)
    password: str = Field(min_length=8, max_length=72)
    display_name: str = Field(default="", max_length=150)
    is_staff: bool = False


class BlockIn(BaseModel):
    """时间盒组块（daily.blocks[] 元素）。"""

    seq: int = 0
    block_name: str = Field(min_length=1, max_length=200)
    input_desc: str = Field(default="", max_length=255)
    key_action: str = Field(default="", max_length=255)
    deliverable: str = Field(default="", max_length=255)
    time_start: str | None = None
    time_end: str | None = None
    is_done: bool = False

    @field_validator("time_start", "time_end", mode="before")
    @classmethod
    def check_time(cls, v):
        return _hhmm(v) if isinstance(v, str) or v is None else v


class DailyIn(BaseModel):
    """每日执行清单写入体：一人一天一条，blocks 随主表整体替换。"""

    plan_date: str
    sleep_hours: float | None = None
    energy_level: EnergyLevel = "medium"
    mit: str = ""

    pre_know_output: bool = False
    pre_materials_ready: bool = False
    pre_phone_handled: bool = False
    during_one_block: bool = False
    during_shrink_step: bool = False
    during_record_distraction: bool = False
    post_visible_output: bool = False
    post_recall: bool = False
    post_next_step: bool = False

    life_water_food: bool = False
    life_walk_exercise: bool = False
    life_clean_tidy: bool = False
    life_social_outdoor: bool = False

    review_accomplished: str = ""
    review_resistance: str = ""
    review_tomorrow_start: str = ""
    review_gratitude: str = ""

    blocks: list[BlockIn] = Field(default_factory=list)

    @field_validator("plan_date", mode="before")
    @classmethod
    def check_date(cls, v):
        return _iso_date(v) if isinstance(v, str) or v is None else v


class DomainIn(BaseModel):
    """周复盘领域回顾（weekly.domains[] 元素）。"""

    domain: DomainName
    facts: str = ""
    status: DomainStatus = "smooth"


class FlowIn(BaseModel):
    """周复盘任务流（weekly.flows[] 元素）。"""

    bucket: Bucket = "todo"
    content: str = ""
    seq: int = 0


class WeeklyIn(BaseModel):
    """每周复盘清单写入体：一人一周一条，domains/flows 随主表整体替换。"""

    week_start: str
    next_mit_1: str = ""
    next_mit_2: str = ""
    next_mit_3: str = ""
    deep_work_time: str = ""
    prep_env_materials: str = ""
    if_condition: str = ""
    then_action: str = ""

    q_skilled_block: bool = False
    q_low_value_time: bool = False
    q_estimate_gap: bool = False
    q_rest_connection: bool = False
    q_next_adjust: bool = False

    domains: list[DomainIn] = Field(default_factory=list)
    flows: list[FlowIn] = Field(default_factory=list)

    @field_validator("week_start", mode="before")
    @classmethod
    def check_date(cls, v):
        return _iso_date(v) if isinstance(v, str) or v is None else v


class HealthIn(BaseModel):
    """系统健康检查（monthly.health_checks[] 元素）。"""

    module: HealthModule
    score: int | None = None
    needed_change: str = ""

    @field_validator("score", mode="before")
    @classmethod
    def check_score(cls, v):
        return _score(v) if v is not None and v != "" else None


class MonthlyIn(BaseModel):
    """每月重构清单写入体：一人一月一条，health_checks 随主表整体替换。"""

    month: str
    top_achievement: str = ""
    worth_continue: WorthChoice = "yes"
    worth_reason: str = ""
    most_valuable_input: str = ""
    biggest_opportunity_cost: str = ""
    keep_item: str = ""
    cut_item: str = ""
    experiment_item: str = ""

    q_skill_portfolio: bool = False
    q_career_alignment: bool = False
    q_life_autonomy: bool = False
    q_recovery_relations: bool = False
    q_longterm_decision: bool = False

    health_checks: list[HealthIn] = Field(default_factory=list)

    @field_validator("month", mode="before")
    @classmethod
    def check_month(cls, v):
        return _iso_month(v) if isinstance(v, str) or v is None else v
