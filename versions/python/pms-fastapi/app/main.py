"""个人效能系统：FastAPI 提供 Vue 页面与 REST API，与其他版本共用数据与 Token 契约。

日 / 周 / 月清单全部按当前用户隔离；明细子表在写请求内随主表整体替换。
契约见 docs/data/api-contract.md。
"""
from __future__ import annotations

from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta
from pathlib import Path

from fastapi import Depends, FastAPI, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from fastapi.staticfiles import StaticFiles

from . import db, security
from .config import settings
from .schemas import DailyIn, LoginIn, MonthlyIn, UserIn, WeeklyIn

USER_FIELDS = "id, username, display_name, is_staff, created_at"
WEB_DIR = Path(__file__).parent / "web"

DAILY_BOOL_FIELDS = (
    "pre_know_output", "pre_materials_ready", "pre_phone_handled",
    "during_one_block", "during_shrink_step", "during_record_distraction",
    "post_visible_output", "post_recall", "post_next_step",
    "life_water_food", "life_walk_exercise", "life_clean_tidy", "life_social_outdoor",
)
DAILY_TEXT_FIELDS = (
    "review_accomplished", "review_resistance", "review_tomorrow_start", "review_gratitude",
)
DAILY_COLS = ("plan_date", "sleep_hours", "energy_level", "mit") + DAILY_BOOL_FIELDS + DAILY_TEXT_FIELDS

BLOCK_COLS = (
    "seq", "block_name", "input_desc", "key_action", "deliverable",
    "time_start", "time_end", "is_done",
)

WEEKLY_BOOL_FIELDS = (
    "q_skilled_block", "q_low_value_time", "q_estimate_gap", "q_rest_connection", "q_next_adjust",
)
WEEKLY_COLS = (
    "week_start", "next_mit_1", "next_mit_2", "next_mit_3", "deep_work_time",
    "prep_env_materials", "if_condition", "then_action",
) + WEEKLY_BOOL_FIELDS
DOMAIN_COLS = ("domain", "facts", "status")
FLOW_COLS = ("bucket", "content", "seq")

MONTHLY_BOOL_FIELDS = (
    "q_skill_portfolio", "q_career_alignment", "q_life_autonomy",
    "q_recovery_relations", "q_longterm_decision",
)
MONTHLY_COLS = (
    "month", "top_achievement", "worth_continue", "worth_reason",
    "most_valuable_input", "biggest_opportunity_cost",
    "keep_item", "cut_item", "experiment_item",
) + MONTHLY_BOOL_FIELDS
HEALTH_COLS = ("module", "score", "needed_change")


@asynccontextmanager
async def lifespan(app: FastAPI):
    db.init_db()
    yield


app = FastAPI(
    title="个人效能系统 API",
    version="2.0.0",
    description="个人效能系统 FastAPI 服务，提供 Vue 页面和 REST API。契约见 docs/data/api-contract.md。",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in settings.cors_origins.split(",")],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.mount(
    "/assets",
    StaticFiles(directory=WEB_DIR / "assets", check_dir=False),
    name="frontend-assets",
)

bearer = HTTPBearer(auto_error=False)


def current_user(cred: HTTPAuthorizationCredentials | None = Depends(bearer)) -> dict:
    claims = security.verify_token(cred.credentials if cred else "")
    if not claims:
        raise HTTPException(status_code=401, detail="未认证或 Token 已过期")
    user = db.query_one(f"SELECT {USER_FIELDS} FROM users WHERE id=%s", (claims.get("uid"),))
    if not user:
        raise HTTPException(status_code=401, detail="用户不存在")
    return user


def admin_user(user: dict = Depends(current_user)) -> dict:
    if not user["is_staff"]:
        raise HTTPException(status_code=403, detail="仅管理员可操作")
    return user


# ------------------------------------------------------------------ 序列化 / 计算字段

def _pick(row: dict, columns, bools=()) -> dict:
    return {c: bool(row.get(c)) if c in bools else row.get(c) for c in columns}


def _time_str(value) -> str | None:
    if value is None or value == "":
        return None
    if isinstance(value, timedelta):
        hours, rem = divmod(int(value.total_seconds()), 3600)
        return f"{hours:02d}:{rem // 60:02d}"
    return str(value)


def _date_str(value) -> str | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        value = value.date()
    if isinstance(value, date):
        return value.isoformat()
    return str(value)


def _block_out(row: dict) -> dict:
    out = {"id": row.get("id"), **_pick(row, BLOCK_COLS, bools=("is_done",))}
    out["time_start"] = _time_str(row.get("time_start"))
    out["time_end"] = _time_str(row.get("time_end"))
    return out


def _load_blocks(plan_id: int) -> list[dict]:
    rows = db.query("SELECT * FROM time_blocks WHERE daily_plan_id=%s ORDER BY seq, id", (plan_id,))
    return [_block_out(r) for r in rows]


def daily_out(row: dict | None) -> dict | None:
    if not row:
        return None
    out = {"id": row["id"], "user_id": row["user_id"]}
    out.update(_pick(row, DAILY_COLS, bools=DAILY_BOOL_FIELDS))
    out["created_at"] = row.get("created_at")
    out["blocks"] = _load_blocks(row["id"])

    total = len(out["blocks"])
    done = sum(1 for b in out["blocks"] if b["is_done"])
    out["blocks_total"] = total
    out["blocks_done"] = done
    out["block_progress"] = round(done * 100 / total) if total else 0
    out["checklist_total"] = 13
    out["checklist_done"] = sum(1 for field in DAILY_BOOL_FIELDS if out[field])
    return out


def weekly_out(row: dict | None) -> dict | None:
    if not row:
        return None
    out = {"id": row["id"], "user_id": row["user_id"]}
    out.update(_pick(row, WEEKLY_COLS, bools=WEEKLY_BOOL_FIELDS))
    out["created_at"] = row.get("created_at")
    try:
        out["week_end"] = (date.fromisoformat(_date_str(row.get("week_start"))) + timedelta(days=6)).isoformat()
    except (TypeError, ValueError):
        out["week_end"] = None
    out["domains"] = [
        {"id": r.get("id"), **_pick(r, DOMAIN_COLS)}
        for r in db.query(
            "SELECT * FROM weekly_domain_reviews WHERE weekly_review_id=%s ORDER BY id", (row["id"],)
        )
    ]
    out["flows"] = [
        {"id": r.get("id"), **_pick(r, FLOW_COLS)}
        for r in db.query(
            "SELECT * FROM weekly_task_flows WHERE weekly_review_id=%s ORDER BY bucket, seq, id", (row["id"],)
        )
    ]
    return out


def monthly_out(row: dict | None) -> dict | None:
    if not row:
        return None
    out = {"id": row["id"], "user_id": row["user_id"]}
    out.update(_pick(row, MONTHLY_COLS, bools=MONTHLY_BOOL_FIELDS))
    out["created_at"] = row.get("created_at")
    out["health_checks"] = [
        {"id": r.get("id"), **_pick(r, HEALTH_COLS)}
        for r in db.query(
            "SELECT * FROM system_health_checks WHERE monthly_review_id=%s ORDER BY id", (row["id"],)
        )
    ]
    scores = [c["score"] for c in out["health_checks"] if c["score"] is not None]
    out["avg_score"] = round(sum(scores) / len(scores), 1) if scores else None
    return out


# ------------------------------------------------------------------ 通用读写 / 404

def _not_found() -> None:
    raise HTTPException(status_code=404, detail="记录不存在")


def _row(table: str, rid: int, uid: int) -> dict | None:
    return db.query_one(f"SELECT * FROM {table} WHERE id=%s AND user_id=%s", (rid, uid))


def _values(obj, columns, bools=()) -> tuple:
    return tuple(int(getattr(obj, c)) if c in bools else getattr(obj, c) for c in columns)


def _insert(table: str, columns, values: tuple) -> int:
    marks = ", ".join(["%s"] * len(columns))
    return db.execute(f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({marks})", values)


def _update(table: str, columns, values: tuple, rid: int, uid: int) -> None:
    sets = ", ".join(f"{c}=%s" for c in columns)
    db.execute(
        f"UPDATE {table} SET {sets}, updated_at=CURRENT_TIMESTAMP WHERE id=%s AND user_id=%s",
        (*values, rid, uid),
    )


def _replace_children(table: str, fk: str, parent_id: int, columns, rows) -> None:
    """同一次写请求内整体替换明细：先删旧行，再逐条插入（autocommit=True）。"""
    db.execute(f"DELETE FROM {table} WHERE {fk}=%s", (parent_id,))
    marks = ", ".join(["%s"] * len(columns))
    sql = f"INSERT INTO {table} ({fk}, {', '.join(columns)}) VALUES (%s, {marks})"
    for row in rows:
        db.execute(sql, (parent_id, *row))


def _ensure_unique(table: str, uid: int, column: str, value, exclude: int = 0) -> None:
    if db.query_one(
        f"SELECT id FROM {table} WHERE user_id=%s AND {column}=%s AND id<>%s", (uid, value, exclude)
    ):
        raise HTTPException(status_code=400, detail="记录已存在")


# ------------------------------------------------------------------ 系统 / 认证 / 用户（保持不变）

@app.get("/", include_in_schema=False)
def root():
    index = WEB_DIR / "index.html"
    if not index.is_file():
        raise HTTPException(
            status_code=503,
            detail="Vue frontend is not built. Run `npm --prefix versions/vue/pms-vue run build` from the repository root.",
        )
    return FileResponse(index)


@app.get("/health", tags=["system"])
def health():
    return {"status": "ok"}


@app.post("/api/auth/login", tags=["auth"])
def login(body: LoginIn):
    row = db.query_one("SELECT * FROM users WHERE username=%s", (body.username,))
    if not row or not security.verify_password(body.password, row["password_hash"]):
        raise HTTPException(status_code=401, detail="用户名或密码错误")
    return {
        "token": security.issue_token(row["id"], row["username"], bool(row["is_staff"])),
        "user": user_out(row),
    }


@app.get("/api/me", tags=["auth"])
def me(user: dict = Depends(current_user)):
    return user


def user_out(row: dict | None) -> dict | None:
    if not row:
        return None
    return {k: row.get(k) for k in ["id", "username", "display_name", "is_staff", "created_at"]}


@app.get("/api/users", tags=["users"])
def list_users(admin: dict = Depends(admin_user)):
    return db.query(f"SELECT {USER_FIELDS} FROM users ORDER BY id")


@app.post("/api/users", status_code=201, tags=["users"])
def create_user(body: UserIn, admin: dict = Depends(admin_user)):
    if db.query_one("SELECT id FROM users WHERE username=%s", (body.username,)):
        raise HTTPException(status_code=400, detail="用户名已存在")
    uid = db.execute(
        "INSERT INTO users (username, password_hash, display_name, is_staff) VALUES (%s, %s, %s, %s)",
        (body.username, security.hash_password(body.password),
         body.display_name or body.username, int(body.is_staff)),
    )
    return user_out(db.query_one(f"SELECT {USER_FIELDS} FROM users WHERE id=%s", (uid,)))


# ------------------------------------------------------------------ 工作台

@app.get("/api/dashboard", tags=["dashboard"])
def dashboard(user: dict = Depends(current_user)):
    uid = user["id"]
    today = date.today()
    monday = today - timedelta(days=today.weekday())
    month = today.strftime("%Y-%m")

    def count(table: str) -> int:
        return int(db.query_one(f"SELECT COUNT(*) AS n FROM {table} WHERE user_id=%s", (uid,))["n"])

    today_plan = db.query_one(
        "SELECT * FROM daily_plans WHERE user_id=%s AND plan_date=%s", (uid, today.isoformat())
    )
    week_review = db.query_one(
        "SELECT * FROM weekly_reviews WHERE user_id=%s AND week_start=%s", (uid, monday.isoformat())
    )
    month_review = db.query_one(
        "SELECT * FROM monthly_reviews WHERE user_id=%s AND month=%s", (uid, month)
    )
    recent_daily = db.query(
        "SELECT * FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC, id DESC LIMIT 5", (uid,)
    )
    recent_weekly = db.query(
        "SELECT * FROM weekly_reviews WHERE user_id=%s ORDER BY week_start DESC, id DESC LIMIT 5", (uid,)
    )
    recent_monthly = db.query(
        "SELECT * FROM monthly_reviews WHERE user_id=%s ORDER BY month DESC, id DESC LIMIT 5", (uid,)
    )
    last_week = db.query(
        "SELECT * FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC, id DESC LIMIT 7", (uid,)
    )
    block_total = block_done = 0
    for row in last_week:
        blocks = _load_blocks(row["id"])
        block_total += len(blocks)
        block_done += sum(1 for b in blocks if b["is_done"])

    return {
        "daily_count": count("daily_plans"),
        "weekly_count": count("weekly_reviews"),
        "monthly_count": count("monthly_reviews"),
        "today_plan": daily_out(today_plan),
        "week_review": weekly_out(week_review),
        "month_review": monthly_out(month_review),
        "recent_daily": [daily_out(r) for r in recent_daily],
        "recent_weekly": [weekly_out(r) for r in recent_weekly],
        "recent_monthly": [monthly_out(r) for r in recent_monthly],
        "block_total": block_total,
        "block_done": block_done,
    }


# ------------------------------------------------------------------ 每日执行清单

@app.get("/api/daily", tags=["daily"])
def list_daily(user: dict = Depends(current_user)):
    rows = db.query(
        "SELECT * FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC, id DESC", (user["id"],)
    )
    return [daily_out(r) for r in rows]


@app.post("/api/daily", status_code=201, tags=["daily"])
def create_daily(body: DailyIn, user: dict = Depends(current_user)):
    uid = user["id"]
    _ensure_unique("daily_plans", uid, "plan_date", body.plan_date)
    did = _insert(
        "daily_plans", ("user_id",) + DAILY_COLS, (uid,) + _values(body, DAILY_COLS, DAILY_BOOL_FIELDS)
    )
    _replace_children(
        "time_blocks", "daily_plan_id", did, BLOCK_COLS,
        [_values(b, BLOCK_COLS, ("is_done",)) for b in body.blocks],
    )
    return daily_out(_row("daily_plans", did, uid))


@app.get("/api/daily/{did}", tags=["daily"])
def get_daily(did: int, user: dict = Depends(current_user)):
    row = _row("daily_plans", did, user["id"])
    if not row:
        _not_found()
    return daily_out(row)


@app.put("/api/daily/{did}", tags=["daily"])
def update_daily(did: int, body: DailyIn, user: dict = Depends(current_user)):
    uid = user["id"]
    if not _row("daily_plans", did, uid):
        _not_found()
    _ensure_unique("daily_plans", uid, "plan_date", body.plan_date, exclude=did)
    _update(
        "daily_plans", DAILY_COLS, _values(body, DAILY_COLS, DAILY_BOOL_FIELDS), did, uid
    )
    _replace_children(
        "time_blocks", "daily_plan_id", did, BLOCK_COLS,
        [_values(b, BLOCK_COLS, ("is_done",)) for b in body.blocks],
    )
    return daily_out(_row("daily_plans", did, uid))


@app.delete("/api/daily/{did}", status_code=204, tags=["daily"])
def delete_daily(did: int, user: dict = Depends(current_user)):
    if not _row("daily_plans", did, user["id"]):
        _not_found()
    db.execute("DELETE FROM daily_plans WHERE id=%s AND user_id=%s", (did, user["id"]))
    return Response(status_code=204)


# ------------------------------------------------------------------ 每周复盘清单

@app.get("/api/weekly", tags=["weekly"])
def list_weekly(user: dict = Depends(current_user)):
    rows = db.query(
        "SELECT * FROM weekly_reviews WHERE user_id=%s ORDER BY week_start DESC, id DESC", (user["id"],)
    )
    return [weekly_out(r) for r in rows]


@app.post("/api/weekly", status_code=201, tags=["weekly"])
def create_weekly(body: WeeklyIn, user: dict = Depends(current_user)):
    uid = user["id"]
    _ensure_unique("weekly_reviews", uid, "week_start", body.week_start)
    rid = _insert(
        "weekly_reviews", ("user_id",) + WEEKLY_COLS,
        (uid,) + _values(body, WEEKLY_COLS, WEEKLY_BOOL_FIELDS),
    )
    _replace_children("weekly_domain_reviews", "weekly_review_id", rid, DOMAIN_COLS,
                      [_values(d, DOMAIN_COLS) for d in body.domains])
    _replace_children("weekly_task_flows", "weekly_review_id", rid, FLOW_COLS,
                      [_values(f, FLOW_COLS) for f in body.flows])
    return weekly_out(_row("weekly_reviews", rid, uid))


@app.get("/api/weekly/{rid}", tags=["weekly"])
def get_weekly(rid: int, user: dict = Depends(current_user)):
    row = _row("weekly_reviews", rid, user["id"])
    if not row:
        _not_found()
    return weekly_out(row)


@app.put("/api/weekly/{rid}", tags=["weekly"])
def update_weekly(rid: int, body: WeeklyIn, user: dict = Depends(current_user)):
    uid = user["id"]
    if not _row("weekly_reviews", rid, uid):
        _not_found()
    _ensure_unique("weekly_reviews", uid, "week_start", body.week_start, exclude=rid)
    _update("weekly_reviews", WEEKLY_COLS, _values(body, WEEKLY_COLS, WEEKLY_BOOL_FIELDS), rid, uid)
    _replace_children("weekly_domain_reviews", "weekly_review_id", rid, DOMAIN_COLS,
                      [_values(d, DOMAIN_COLS) for d in body.domains])
    _replace_children("weekly_task_flows", "weekly_review_id", rid, FLOW_COLS,
                      [_values(f, FLOW_COLS) for f in body.flows])
    return weekly_out(_row("weekly_reviews", rid, uid))


@app.delete("/api/weekly/{rid}", status_code=204, tags=["weekly"])
def delete_weekly(rid: int, user: dict = Depends(current_user)):
    if not _row("weekly_reviews", rid, user["id"]):
        _not_found()
    db.execute("DELETE FROM weekly_reviews WHERE id=%s AND user_id=%s", (rid, user["id"]))
    return Response(status_code=204)


# ------------------------------------------------------------------ 每月重构清单

@app.get("/api/monthly", tags=["monthly"])
def list_monthly(user: dict = Depends(current_user)):
    rows = db.query(
        "SELECT * FROM monthly_reviews WHERE user_id=%s ORDER BY month DESC, id DESC", (user["id"],)
    )
    return [monthly_out(r) for r in rows]


@app.post("/api/monthly", status_code=201, tags=["monthly"])
def create_monthly(body: MonthlyIn, user: dict = Depends(current_user)):
    uid = user["id"]
    _ensure_unique("monthly_reviews", uid, "month", body.month)
    rid = _insert(
        "monthly_reviews", ("user_id",) + MONTHLY_COLS,
        (uid,) + _values(body, MONTHLY_COLS, MONTHLY_BOOL_FIELDS),
    )
    _replace_children("system_health_checks", "monthly_review_id", rid, HEALTH_COLS,
                      [_values(h, HEALTH_COLS) for h in body.health_checks])
    return monthly_out(_row("monthly_reviews", rid, uid))


@app.get("/api/monthly/{rid}", tags=["monthly"])
def get_monthly(rid: int, user: dict = Depends(current_user)):
    row = _row("monthly_reviews", rid, user["id"])
    if not row:
        _not_found()
    return monthly_out(row)


@app.put("/api/monthly/{rid}", tags=["monthly"])
def update_monthly(rid: int, body: MonthlyIn, user: dict = Depends(current_user)):
    uid = user["id"]
    if not _row("monthly_reviews", rid, uid):
        _not_found()
    _ensure_unique("monthly_reviews", uid, "month", body.month, exclude=rid)
    _update("monthly_reviews", MONTHLY_COLS, _values(body, MONTHLY_COLS, MONTHLY_BOOL_FIELDS), rid, uid)
    _replace_children("system_health_checks", "monthly_review_id", rid, HEALTH_COLS,
                      [_values(h, HEALTH_COLS) for h in body.health_checks])
    return monthly_out(_row("monthly_reviews", rid, uid))


@app.delete("/api/monthly/{rid}", status_code=204, tags=["monthly"])
def delete_monthly(rid: int, user: dict = Depends(current_user)):
    if not _row("monthly_reviews", rid, user["id"]):
        _not_found()
    db.execute("DELETE FROM monthly_reviews WHERE id=%s AND user_id=%s", (rid, user["id"]))
    return Response(status_code=204)
