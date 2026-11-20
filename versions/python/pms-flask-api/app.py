"""Flask REST API implementing the shared personal-effectiveness API contract."""
from __future__ import annotations

from datetime import date, datetime, timedelta
from typing import Any, Type

from flask import Flask, g, jsonify, request
from pydantic import BaseModel, ValidationError
import pymysql

import db
import security
from config import settings
from schemas import DailyIn, LoginIn, MonthlyIn, UserIn, WeeklyIn

USER_FIELDS = "id, username, display_name, is_staff, created_at"
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
    "most_valuable_input", "biggest_opportunity_cost", "keep_item", "cut_item", "experiment_item",
) + MONTHLY_BOOL_FIELDS
HEALTH_COLS = ("module", "score", "needed_change")


class ApiProblem(Exception):
    def __init__(self, status: int, detail: str):
        super().__init__(detail)
        self.status = status
        self.detail = detail


def _validation_message(error: ValidationError) -> str:
    details = error.errors()
    message = details[0]["msg"] if details else "请求字段无效"
    return message.removeprefix("Value error, ")


def _pick(row: dict, columns: tuple[str, ...], bools: tuple[str, ...] = ()) -> dict:
    return {column: bool(row.get(column)) if column in bools else row.get(column) for column in columns}


def _time_str(value: Any) -> str | None:
    if value is None or value == "":
        return None
    if isinstance(value, timedelta):
        hours, remainder = divmod(int(value.total_seconds()), 3600)
        return f"{hours:02d}:{remainder // 60:02d}"
    return str(value)[:5]


def _date_str(value: Any) -> str | None:
    if value is None:
        return None
    if isinstance(value, datetime):
        value = value.date()
    return value.isoformat() if isinstance(value, date) else str(value)


def _load_blocks(plan_id: int) -> list[dict]:
    rows = db.query("SELECT * FROM time_blocks WHERE daily_plan_id=%s ORDER BY seq, id", (plan_id,))
    result = []
    for row in rows:
        item = {"id": row.get("id"), **_pick(row, BLOCK_COLS, ("is_done",))}
        item["time_start"] = _time_str(row.get("time_start"))
        item["time_end"] = _time_str(row.get("time_end"))
        result.append(item)
    return result


def daily_out(row: dict | None) -> dict | None:
    if not row:
        return None
    output = {"id": row["id"], "user_id": row["user_id"]}
    output.update(_pick(row, DAILY_COLS, DAILY_BOOL_FIELDS))
    output["created_at"] = row.get("created_at")
    output["blocks"] = _load_blocks(row["id"])
    total = len(output["blocks"])
    done = sum(1 for block in output["blocks"] if block["is_done"])
    output.update(
        blocks_total=total,
        blocks_done=done,
        block_progress=round(done * 100 / total) if total else 0,
        checklist_total=13,
        checklist_done=sum(1 for field in DAILY_BOOL_FIELDS if output[field]),
    )
    return output


def weekly_out(row: dict | None) -> dict | None:
    if not row:
        return None
    output = {"id": row["id"], "user_id": row["user_id"]}
    output.update(_pick(row, WEEKLY_COLS, WEEKLY_BOOL_FIELDS))
    output["created_at"] = row.get("created_at")
    try:
        output["week_end"] = (
            date.fromisoformat(_date_str(row.get("week_start"))) + timedelta(days=6)
        ).isoformat()
    except (TypeError, ValueError):
        output["week_end"] = None
    output["domains"] = [
        {"id": item.get("id"), **_pick(item, DOMAIN_COLS)}
        for item in db.query(
            "SELECT * FROM weekly_domain_reviews WHERE weekly_review_id=%s ORDER BY id",
            (row["id"],),
        )
    ]
    output["flows"] = [
        {"id": item.get("id"), **_pick(item, FLOW_COLS)}
        for item in db.query(
            "SELECT * FROM weekly_task_flows WHERE weekly_review_id=%s ORDER BY bucket, seq, id",
            (row["id"],),
        )
    ]
    return output


def monthly_out(row: dict | None) -> dict | None:
    if not row:
        return None
    output = {"id": row["id"], "user_id": row["user_id"]}
    output.update(_pick(row, MONTHLY_COLS, MONTHLY_BOOL_FIELDS))
    output["created_at"] = row.get("created_at")
    output["health_checks"] = [
        {"id": item.get("id"), **_pick(item, HEALTH_COLS)}
        for item in db.query(
            "SELECT * FROM system_health_checks WHERE monthly_review_id=%s ORDER BY id",
            (row["id"],),
        )
    ]
    scores = [item["score"] for item in output["health_checks"] if item["score"] is not None]
    output["avg_score"] = round(sum(scores) / len(scores), 1) if scores else None
    return output


def _require_user() -> dict:
    authorization = request.headers.get("Authorization", "")
    scheme, _, token = authorization.partition(" ")
    claims = security.verify_token(token) if scheme.lower() == "bearer" else None
    if not claims:
        raise ApiProblem(401, "未认证或 Token 已过期")
    user = db.query_one(f"SELECT {USER_FIELDS} FROM users WHERE id=%s", (claims.get("uid"),))
    if not user:
        raise ApiProblem(401, "用户不存在")
    return _user_out(user)


def _require_admin() -> dict:
    user = g.current_user
    if not user["is_staff"]:
        raise ApiProblem(403, "仅管理员可操作")
    return user


def _model(model_type: Type[BaseModel]) -> BaseModel:
    if not request.is_json:
        raise ApiProblem(400, "请求体必须为 JSON")
    data = request.get_json(silent=True)
    if data is None:
        raise ApiProblem(400, "JSON 请求格式错误")
    try:
        return model_type.model_validate(data)
    except ValidationError as error:
        raise ApiProblem(400, _validation_message(error)) from error


def _row(table: str, record_id: int, user_id: int) -> dict | None:
    return db.query_one(f"SELECT * FROM {table} WHERE id=%s AND user_id=%s", (record_id, user_id))


def _not_found() -> None:
    raise ApiProblem(404, "记录不存在")


def _user_out(row: dict | None) -> dict | None:
    if not row:
        return None
    return {
        "id": row.get("id"),
        "username": row.get("username"),
        "display_name": row.get("display_name"),
        "is_staff": bool(row.get("is_staff")),
        "created_at": row.get("created_at"),
    }


def _values(model: BaseModel, columns: tuple[str, ...], bools: tuple[str, ...] = ()) -> tuple:
    return tuple(int(getattr(model, column)) if column in bools else getattr(model, column) for column in columns)


def _insert(cursor, table: str, columns: tuple[str, ...], values: tuple) -> int:
    marks = ", ".join(["%s"] * len(columns))
    cursor.execute(f"INSERT INTO {table} ({', '.join(columns)}) VALUES ({marks})", values)
    return cursor.lastrowid


def _update(cursor, table: str, columns: tuple[str, ...], values: tuple, record_id: int, user_id: int) -> None:
    assignments = ", ".join(f"{column}=%s" for column in columns)
    cursor.execute(
        f"UPDATE {table} SET {assignments}, updated_at=CURRENT_TIMESTAMP "
        "WHERE id=%s AND user_id=%s",
        (*values, record_id, user_id),
    )


def _replace_children(cursor, table: str, foreign_key: str, parent_id: int,
                      columns: tuple[str, ...], rows: list[tuple]) -> None:
    cursor.execute(f"DELETE FROM {table} WHERE {foreign_key}=%s", (parent_id,))
    marks = ", ".join(["%s"] * len(columns))
    statement = f"INSERT INTO {table} ({foreign_key}, {', '.join(columns)}) VALUES (%s, {marks})"
    for row in rows:
        cursor.execute(statement, (parent_id, *row))


def _ensure_unique(cursor, table: str, user_id: int, column: str, value: Any, exclude: int = 0) -> None:
    cursor.execute(
        f"SELECT id FROM {table} WHERE user_id=%s AND {column}=%s AND id<>%s",
        (user_id, value, exclude),
    )
    if cursor.fetchone():
        raise ApiProblem(400, "记录已存在")


def create_app(test_config: dict | None = None) -> Flask:
    app = Flask(__name__)
    app.config.update(SECRET_KEY=settings.api_secret, JSON_SORT_KEYS=False)
    if test_config:
        app.config.update(test_config)

    @app.before_request
    def initialize_database():
        if not app.config.get("TESTING") and not app.extensions.get("pms_database_initialized"):
            db.init_db()
            app.extensions["pms_database_initialized"] = True

    @app.before_request
    def load_api_user():
        if (
            request.method != "OPTIONS"
            and request.path.startswith("/api/")
            and request.path != "/api/auth/login"
        ):
            g.current_user = _require_user()

    @app.after_request
    def cors(response):
        origin = request.headers.get("Origin")
        allowed = [item.strip() for item in settings.cors_origins.split(",") if item.strip()]
        if origin and ("*" in allowed or origin in allowed):
            response.headers["Access-Control-Allow-Origin"] = origin
            response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
            response.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type"
            response.headers.add("Vary", "Origin")
        return response

    @app.errorhandler(ApiProblem)
    def handle_api_problem(error: ApiProblem):
        return jsonify(detail=error.detail), error.status

    @app.errorhandler(ValidationError)
    def handle_validation_error(error: ValidationError):
        return jsonify(detail=_validation_message(error)), 400

    @app.errorhandler(404)
    def handle_not_found(_error):
        return jsonify(detail="记录不存在"), 404

    @app.errorhandler(405)
    def handle_method_not_allowed(_error):
        return jsonify(detail="请求方法不支持"), 405

    @app.errorhandler(pymysql.err.IntegrityError)
    def handle_integrity_error(error: pymysql.err.IntegrityError):
        if error.args and error.args[0] == 1062:
            return jsonify(detail="记录或明细中存在重复值"), 400
        raise error

    @app.get("/")
    def root():
        return jsonify(service="PMS Flask API", health="/health", api="/api")

    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.post("/api/auth/login")
    def login():
        body = _model(LoginIn)
        row = db.query_one("SELECT * FROM users WHERE username=%s", (body.username,))
        if not row or not security.verify_password(body.password, row["password_hash"]):
            raise ApiProblem(401, "用户名或密码错误")
        return {
            "token": security.issue_token(row["id"], row["username"], bool(row["is_staff"])),
            "user": _user_out(row),
        }

    @app.get("/api/me")
    def me():
        return g.current_user

    @app.get("/api/users")
    def list_users():
        _require_admin()
        return jsonify([_user_out(row) for row in db.query(f"SELECT {USER_FIELDS} FROM users ORDER BY id")])

    @app.post("/api/users")
    def create_user():
        _require_admin()
        body = _model(UserIn)
        if db.query_one("SELECT id FROM users WHERE username=%s", (body.username,)):
            raise ApiProblem(400, "用户名已存在")
        uid = db.execute(
            "INSERT INTO users (username, password_hash, display_name, is_staff) "
            "VALUES (%s, %s, %s, %s)",
            (body.username, security.hash_password(body.password),
             body.display_name or body.username, int(body.is_staff)),
        )
        return _user_out(db.query_one(f"SELECT {USER_FIELDS} FROM users WHERE id=%s", (uid,))), 201

    @app.get("/api/dashboard")
    def dashboard():
        user_id = g.current_user["id"]
        today = date.today()
        monday = today - timedelta(days=today.weekday())
        month = today.strftime("%Y-%m")

        def count(table: str) -> int:
            result = db.query_one(f"SELECT COUNT(*) AS n FROM {table} WHERE user_id=%s", (user_id,))
            return int(result["n"])

        today_plan = db.query_one(
            "SELECT * FROM daily_plans WHERE user_id=%s AND plan_date=%s",
            (user_id, today.isoformat()),
        )
        week_review = db.query_one(
            "SELECT * FROM weekly_reviews WHERE user_id=%s AND week_start=%s",
            (user_id, monday.isoformat()),
        )
        month_review = db.query_one(
            "SELECT * FROM monthly_reviews WHERE user_id=%s AND month=%s", (user_id, month)
        )
        recent_daily = db.query(
            "SELECT * FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC, id DESC LIMIT 5",
            (user_id,),
        )
        recent_weekly = db.query(
            "SELECT * FROM weekly_reviews WHERE user_id=%s ORDER BY week_start DESC, id DESC LIMIT 5",
            (user_id,),
        )
        recent_monthly = db.query(
            "SELECT * FROM monthly_reviews WHERE user_id=%s ORDER BY month DESC, id DESC LIMIT 5",
            (user_id,),
        )
        last_week = db.query(
            "SELECT * FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC, id DESC LIMIT 7",
            (user_id,),
        )
        block_total = block_done = 0
        for row in last_week:
            blocks = _load_blocks(row["id"])
            block_total += len(blocks)
            block_done += sum(1 for block in blocks if block["is_done"])
        return {
            "daily_count": count("daily_plans"),
            "weekly_count": count("weekly_reviews"),
            "monthly_count": count("monthly_reviews"),
            "today_plan": daily_out(today_plan),
            "week_review": weekly_out(week_review),
            "month_review": monthly_out(month_review),
            "recent_daily": [daily_out(row) for row in recent_daily],
            "recent_weekly": [weekly_out(row) for row in recent_weekly],
            "recent_monthly": [monthly_out(row) for row in recent_monthly],
            "block_total": block_total,
            "block_done": block_done,
        }

    @app.get("/api/daily")
    def list_daily():
        rows = db.query(
            "SELECT * FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC, id DESC",
            (g.current_user["id"],),
        )
        return jsonify([daily_out(row) for row in rows])

    @app.post("/api/daily")
    def create_daily():
        body = _model(DailyIn)
        user_id = g.current_user["id"]
        with db.transaction() as conn, conn.cursor() as cursor:
            _ensure_unique(cursor, "daily_plans", user_id, "plan_date", body.plan_date)
            daily_id = _insert(
                cursor, "daily_plans", ("user_id",) + DAILY_COLS,
                (user_id,) + _values(body, DAILY_COLS, DAILY_BOOL_FIELDS),
            )
            _replace_children(
                cursor, "time_blocks", "daily_plan_id", daily_id, BLOCK_COLS,
                [_values(block, BLOCK_COLS, ("is_done",)) for block in body.blocks],
            )
        return daily_out(_row("daily_plans", daily_id, user_id)), 201

    @app.get("/api/daily/<int:record_id>")
    def get_daily(record_id: int):
        row = _row("daily_plans", record_id, g.current_user["id"])
        if not row:
            _not_found()
        return daily_out(row)

    @app.put("/api/daily/<int:record_id>")
    def update_daily(record_id: int):
        body = _model(DailyIn)
        user_id = g.current_user["id"]
        if not _row("daily_plans", record_id, user_id):
            _not_found()
        with db.transaction() as conn, conn.cursor() as cursor:
            _ensure_unique(cursor, "daily_plans", user_id, "plan_date", body.plan_date, record_id)
            _update(cursor, "daily_plans", DAILY_COLS,
                    _values(body, DAILY_COLS, DAILY_BOOL_FIELDS), record_id, user_id)
            _replace_children(
                cursor, "time_blocks", "daily_plan_id", record_id, BLOCK_COLS,
                [_values(block, BLOCK_COLS, ("is_done",)) for block in body.blocks],
            )
        return daily_out(_row("daily_plans", record_id, user_id))

    @app.delete("/api/daily/<int:record_id>")
    def delete_daily(record_id: int):
        user_id = g.current_user["id"]
        if not _row("daily_plans", record_id, user_id):
            _not_found()
        db.execute("DELETE FROM daily_plans WHERE id=%s AND user_id=%s", (record_id, user_id))
        return "", 204

    @app.get("/api/weekly")
    def list_weekly():
        rows = db.query(
            "SELECT * FROM weekly_reviews WHERE user_id=%s ORDER BY week_start DESC, id DESC",
            (g.current_user["id"],),
        )
        return jsonify([weekly_out(row) for row in rows])

    @app.post("/api/weekly")
    def create_weekly():
        body = _model(WeeklyIn)
        user_id = g.current_user["id"]
        with db.transaction() as conn, conn.cursor() as cursor:
            _ensure_unique(cursor, "weekly_reviews", user_id, "week_start", body.week_start)
            review_id = _insert(
                cursor, "weekly_reviews", ("user_id",) + WEEKLY_COLS,
                (user_id,) + _values(body, WEEKLY_COLS, WEEKLY_BOOL_FIELDS),
            )
            _replace_children(
                cursor, "weekly_domain_reviews", "weekly_review_id", review_id, DOMAIN_COLS,
                [_values(item, DOMAIN_COLS) for item in body.domains],
            )
            _replace_children(
                cursor, "weekly_task_flows", "weekly_review_id", review_id, FLOW_COLS,
                [_values(item, FLOW_COLS) for item in body.flows],
            )
        return weekly_out(_row("weekly_reviews", review_id, user_id)), 201

    @app.get("/api/weekly/<int:record_id>")
    def get_weekly(record_id: int):
        row = _row("weekly_reviews", record_id, g.current_user["id"])
        if not row:
            _not_found()
        return weekly_out(row)

    @app.put("/api/weekly/<int:record_id>")
    def update_weekly(record_id: int):
        body = _model(WeeklyIn)
        user_id = g.current_user["id"]
        if not _row("weekly_reviews", record_id, user_id):
            _not_found()
        with db.transaction() as conn, conn.cursor() as cursor:
            _ensure_unique(cursor, "weekly_reviews", user_id, "week_start", body.week_start, record_id)
            _update(cursor, "weekly_reviews", WEEKLY_COLS,
                    _values(body, WEEKLY_COLS, WEEKLY_BOOL_FIELDS), record_id, user_id)
            _replace_children(
                cursor, "weekly_domain_reviews", "weekly_review_id", record_id, DOMAIN_COLS,
                [_values(item, DOMAIN_COLS) for item in body.domains],
            )
            _replace_children(
                cursor, "weekly_task_flows", "weekly_review_id", record_id, FLOW_COLS,
                [_values(item, FLOW_COLS) for item in body.flows],
            )
        return weekly_out(_row("weekly_reviews", record_id, user_id))

    @app.delete("/api/weekly/<int:record_id>")
    def delete_weekly(record_id: int):
        user_id = g.current_user["id"]
        if not _row("weekly_reviews", record_id, user_id):
            _not_found()
        db.execute("DELETE FROM weekly_reviews WHERE id=%s AND user_id=%s", (record_id, user_id))
        return "", 204

    @app.get("/api/monthly")
    def list_monthly():
        rows = db.query(
            "SELECT * FROM monthly_reviews WHERE user_id=%s ORDER BY month DESC, id DESC",
            (g.current_user["id"],),
        )
        return jsonify([monthly_out(row) for row in rows])

    @app.post("/api/monthly")
    def create_monthly():
        body = _model(MonthlyIn)
        user_id = g.current_user["id"]
        with db.transaction() as conn, conn.cursor() as cursor:
            _ensure_unique(cursor, "monthly_reviews", user_id, "month", body.month)
            review_id = _insert(
                cursor, "monthly_reviews", ("user_id",) + MONTHLY_COLS,
                (user_id,) + _values(body, MONTHLY_COLS, MONTHLY_BOOL_FIELDS),
            )
            _replace_children(
                cursor, "system_health_checks", "monthly_review_id", review_id, HEALTH_COLS,
                [_values(item, HEALTH_COLS) for item in body.health_checks],
            )
        return monthly_out(_row("monthly_reviews", review_id, user_id)), 201

    @app.get("/api/monthly/<int:record_id>")
    def get_monthly(record_id: int):
        row = _row("monthly_reviews", record_id, g.current_user["id"])
        if not row:
            _not_found()
        return monthly_out(row)

    @app.put("/api/monthly/<int:record_id>")
    def update_monthly(record_id: int):
        body = _model(MonthlyIn)
        user_id = g.current_user["id"]
        if not _row("monthly_reviews", record_id, user_id):
            _not_found()
        with db.transaction() as conn, conn.cursor() as cursor:
            _ensure_unique(cursor, "monthly_reviews", user_id, "month", body.month, record_id)
            _update(cursor, "monthly_reviews", MONTHLY_COLS,
                    _values(body, MONTHLY_COLS, MONTHLY_BOOL_FIELDS), record_id, user_id)
            _replace_children(
                cursor, "system_health_checks", "monthly_review_id", record_id, HEALTH_COLS,
                [_values(item, HEALTH_COLS) for item in body.health_checks],
            )
        return monthly_out(_row("monthly_reviews", record_id, user_id))

    @app.delete("/api/monthly/<int:record_id>")
    def delete_monthly(record_id: int):
        user_id = g.current_user["id"]
        if not _row("monthly_reviews", record_id, user_id):
            _not_found()
        db.execute("DELETE FROM monthly_reviews WHERE id=%s AND user_id=%s", (record_id, user_id))
        return "", 204

    return app


app = create_app()

if __name__ == "__main__":
    app.run(host="127.0.0.1", port=settings.server_port, debug=True)
