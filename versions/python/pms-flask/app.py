"""Server-rendered Flask monolith for the personal effectiveness system."""
from __future__ import annotations

import hmac
import secrets
from datetime import date, timedelta
from typing import Any
from urllib.parse import urlsplit

import pymysql
from flask import Flask, abort, flash, g, redirect, render_template, request, session, url_for
from pydantic import BaseModel, ValidationError

import db
import security
from config import settings
from schemas import DailyIn, MonthlyIn, UserIn, WeeklyIn

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

FIELD_LABELS = {
    "plan_date": ("计划日期", "date"),
    "sleep_hours": ("睡眠小时", "number"),
    "energy_level": ("精力水平", "select:low:低:medium:中:high:高"),
    "mit": ("今日最重要成果（MIT）", "textarea"),
    "pre_know_output": ("我知道本轮要交付什么", "checkbox"),
    "pre_materials_ready": ("材料已准备", "checkbox"),
    "pre_phone_handled": ("手机/通知已处理", "checkbox"),
    "during_one_block": ("一次只做一个组块", "checkbox"),
    "during_shrink_step": ("卡住时先缩小下一步", "checkbox"),
    "during_record_distraction": ("必要时记录干扰", "checkbox"),
    "post_visible_output": ("有可见产出", "checkbox"),
    "post_recall": ("做了主动回忆/复述", "checkbox"),
    "post_next_step": ("标记下一步", "checkbox"),
    "life_water_food": ("饮水/正常饮食", "checkbox"),
    "life_walk_exercise": ("走路/运动", "checkbox"),
    "life_clean_tidy": ("清洁/整理", "checkbox"),
    "life_social_outdoor": ("真实交流/户外", "checkbox"),
    "review_accomplished": ("今天真正完成了什么", "textarea"),
    "review_resistance": ("今天最大的阻力是什么", "textarea"),
    "review_tomorrow_start": ("明天最小的启动动作", "textarea"),
    "review_gratitude": ("今天值得感谢/享受的一件事", "textarea"),
    "week_start": ("本周起始日（周一）", "date"),
    "next_mit_1": ("下周最重要成果 1", "text"),
    "next_mit_2": ("下周最重要成果 2", "text"),
    "next_mit_3": ("下周最重要成果 3", "text"),
    "deep_work_time": ("要保护的深度时间", "text"),
    "prep_env_materials": ("需要提前准备的环境/材料", "textarea"),
    "if_condition": ("If：如果……", "text"),
    "then_action": ("Then：那么我就……", "text"),
    "q_skilled_block": ("哪个组块本周变得更熟练？", "checkbox"),
    "q_low_value_time": ("哪件事耗时很多但价值很低？", "checkbox"),
    "q_estimate_gap": ("哪个时间盒估算偏差最大？为什么？", "checkbox"),
    "q_rest_connection": ("本周有没有休息、快乐和真实连接？", "checkbox"),
    "q_next_adjust": ("下周要继续、调整、停止什么？", "checkbox"),
    "month": ("月份", "month"),
    "top_achievement": ("本月最重要的成果", "textarea"),
    "worth_continue": ("是否仍值得投入", "select:yes:是:adjust:调整:stop:停止"),
    "worth_reason": ("判断原因", "text"),
    "most_valuable_input": ("本月最有价值的投入", "textarea"),
    "biggest_opportunity_cost": ("本月最大的机会成本", "textarea"),
    "keep_item": ("保留（有效的）", "textarea"),
    "cut_item": ("删减（低价值的）", "textarea"),
    "experiment_item": ("实验（新的最小改变）", "textarea"),
    "q_skill_portfolio": ("技能是否形成可展示作品？", "checkbox"),
    "q_career_alignment": ("工作是否更接近长期方向？", "checkbox"),
    "q_life_autonomy": ("生活是否更有自主性？", "checkbox"),
    "q_recovery_relations": ("是否有足够的恢复与关系投入？", "checkbox"),
    "q_longterm_decision": ("是否应继续、转向或停止长期投资？", "checkbox"),
}
CHILD_FIELDS = {
    "blocks": [
        ("seq", "序号", "number", "0"),
        ("block_name", "组块/任务", "text", ""),
        ("input_desc", "输入", "text", ""),
        ("key_action", "关键动作", "text", ""),
        ("deliverable", "可验收输出", "text", ""),
        ("time_start", "开始", "time", ""),
        ("time_end", "结束", "time", ""),
        ("is_done", "完成", "checkbox", ""),
    ],
    "domains": [
        ("domain", "领域", "select:work:工作:study:学习:body:身体:relations:关系", "work"),
        ("facts", "本周事实/产出", "textarea", ""),
        ("status", "状态", "select:smooth:顺利:blocked:受阻:paused:暂停", "smooth"),
    ],
    "flows": [
        ("bucket", "分栏", "select:todo:待办:doing:进行中:done:已完成:remove:删除/委托/延后", "todo"),
        ("content", "内容", "text", ""),
        ("seq", "序号", "number", "0"),
    ],
    "health_checks": [
        ("module", "模块", "select:direction:方向清晰度:taskload:任务负荷:blockquality:组块质量:timebox:时间盒:sleepenergy:睡眠体能:reviewquality:复盘反馈", "direction"),
        ("score", "自评（1-5）", "select::未评分:1:1:2:2:3:3:4:4:5:5", ""),
        ("needed_change", "需要的改变", "textarea", ""),
    ],
}
RECORDS = {
    "daily": {
        "title": "每日执行清单", "table": "daily_plans", "unique": "plan_date",
        "columns": DAILY_COLS, "bools": DAILY_BOOL_FIELDS, "model": DailyIn,
        "children": (("blocks", "time_blocks", "daily_plan_id", BLOCK_COLS, ("is_done",)),),
        "defaults": {"energy_level": "medium"},
        "child_defaults": {"blocks": [{"seq": index} for index in range(1, 5)]},
    },
    "weekly": {
        "title": "每周复盘清单", "table": "weekly_reviews", "unique": "week_start",
        "columns": WEEKLY_COLS, "bools": WEEKLY_BOOL_FIELDS, "model": WeeklyIn,
        "children": (
            ("domains", "weekly_domain_reviews", "weekly_review_id", DOMAIN_COLS, ()),
            ("flows", "weekly_task_flows", "weekly_review_id", FLOW_COLS, ()),
        ),
        "defaults": {},
        "child_defaults": {
            "domains": [{"domain": name, "status": "smooth"} for name in ("work", "study", "body", "relations")],
            "flows": [{"bucket": "todo", "seq": index} for index in range(1, 5)],
        },
    },
    "monthly": {
        "title": "每月重构清单", "table": "monthly_reviews", "unique": "month",
        "columns": MONTHLY_COLS, "bools": MONTHLY_BOOL_FIELDS, "model": MonthlyIn,
        "children": (("health_checks", "system_health_checks", "monthly_review_id", HEALTH_COLS, ()),),
        "defaults": {"worth_continue": "yes"},
        "child_defaults": {
            "health_checks": [
                {"module": name}
                for name in ("direction", "taskload", "blockquality", "timebox", "sleepenergy", "reviewquality")
            ],
        },
    },
}


def _validation_message(error: ValidationError) -> str:
    errors = error.errors()
    message = errors[0]["msg"] if errors else "表单字段无效"
    return message.removeprefix("Value error, ")


def _field_specs(columns: tuple[str, ...]) -> list[dict]:
    result = []
    for name in columns:
        label, kind = FIELD_LABELS[name]
        options = []
        if kind.startswith("select:"):
            parts = kind.split(":")[1:]
            options = [{"value": parts[i], "label": parts[i + 1]} for i in range(0, len(parts), 2)]
            kind = "select"
        result.append({"name": name, "label": label, "type": kind, "options": options})
    return result


def _child_specs(kind: str) -> list[dict]:
    specs = []
    for name, table, foreign_key, columns, bools in RECORDS[kind]["children"]:
        specs.append({
            "name": name,
            "label": {
                "blocks": "时间盒组块", "domains": "领域事实", "flows": "任务流",
                "health_checks": "系统健康检查",
            }[name],
            "fields": _fields_for_child(name),
        })
    return specs


def _fields_for_child(group_name: str) -> list[dict]:
    result = []
    for field_name, label, input_type, default in CHILD_FIELDS[group_name]:
        options = []
        if input_type.startswith("select:"):
            parts = input_type.split(":")[1:]
            options = [
                {"value": parts[index], "label": parts[index + 1]}
                for index in range(0, len(parts), 2)
            ]
            input_type = "select"
        result.append({
            "name": field_name, "label": label, "type": input_type,
            "default": default, "options": options,
        })
    return result


def _parse_child_rows(group_name: str, group_fields: list[tuple]) -> list[dict]:
    try:
        count = int(request.form.get(f"{group_name}-TOTAL_FORMS", "0"))
    except ValueError as error:
        raise ValueError(f"{group_name} 表单行数无效") from error
    if count < 0 or count > 100:
        raise ValueError(f"{group_name} 最多允许 100 行")
    rows = []
    for index in range(count):
        row = {}
        for name, _label, input_type, _default in group_fields:
            key = f"{group_name}-{index}-{name}"
            if input_type == "checkbox":
                row[name] = key in request.form
            else:
                row[name] = request.form.get(key, "")
        if group_name == "blocks" and not row.get("block_name"):
            continue
        if group_name == "flows" and not row.get("content"):
            continue
        rows.append(row)
    return rows


def _form_payload(kind: str):
    spec = RECORDS[kind]
    payload: dict[str, Any] = {}
    for field in _field_specs(spec["columns"]):
        name = field["name"]
        if field["type"] == "checkbox":
            payload[name] = name in request.form
        else:
            value = request.form.get(name, "")
            payload[name] = None if name == "sleep_hours" and value == "" else value
    for group_name, *_ in spec["children"]:
        payload[group_name] = _parse_child_rows(group_name, CHILD_FIELDS[group_name])
    return spec["model"].model_validate(payload)


def _form_state(kind: str, row: dict | None = None, children: dict | None = None) -> tuple[dict, list[dict]]:
    spec = RECORDS[kind]
    values = dict(spec["defaults"])
    today = date.today()
    if kind == "daily":
        values["plan_date"] = today.isoformat()
    elif kind == "weekly":
        values["week_start"] = (today - timedelta(days=today.weekday())).isoformat()
    elif kind == "monthly":
        values["month"] = today.strftime("%Y-%m")
    if row:
        values.update(row)
    groups = []
    for group_name, *_ in spec["children"]:
        rows = (children or {}).get(group_name)
        if rows is None:
            rows = spec["child_defaults"].get(group_name, [])
        groups.append({
            "name": group_name,
            "label": {
                "blocks": "时间盒组块", "domains": "领域事实", "flows": "任务流",
                "health_checks": "系统健康检查",
            }[group_name],
            "fields": _fields_for_child(group_name),
            "rows": rows,
        })
    return values, groups


def _submitted_groups(kind: str) -> list[dict]:
    groups = _form_state(kind)[1]
    for group in groups:
        try:
            group["rows"] = _parse_child_rows(group["name"], CHILD_FIELDS[group["name"]])
        except ValueError:
            pass
    return groups


def _record_data(kind: str, row: dict) -> dict:
    spec = RECORDS[kind]
    output = dict(row)
    for group_name, table, foreign_key, _columns, _bools in spec["children"]:
        output[group_name] = db.query(
            f"SELECT * FROM {table} WHERE {foreign_key}=%s ORDER BY id", (row["id"],)
        )
    if kind == "weekly":
        output["week_end"] = (
            date.fromisoformat(str(row["week_start"])) + timedelta(days=6)
        ).isoformat()
    if kind == "daily":
        blocks = output["blocks"]
        output["blocks_total"] = len(blocks)
        output["blocks_done"] = sum(bool(block["is_done"]) for block in blocks)
        output["block_progress"] = round(output["blocks_done"] * 100 / len(blocks)) if blocks else 0
    if kind == "monthly":
        scores = [item["score"] for item in output["health_checks"] if item.get("score") is not None]
        output["avg_score"] = round(sum(scores) / len(scores), 1) if scores else None
    return output


def _replace_children(cursor, kind: str, group_name: str, parent_id: int, rows: list[BaseModel]) -> None:
    groups = {item[0]: item[1:] for item in RECORDS[kind]["children"]}
    table, foreign_key, columns, bools = groups[group_name]
    cursor.execute(f"DELETE FROM {table} WHERE {foreign_key}=%s", (parent_id,))
    marks = ", ".join(["%s"] * len(columns))
    statement = f"INSERT INTO {table} ({foreign_key}, {', '.join(columns)}) VALUES (%s, {marks})"
    for row in rows:
        values = tuple(
            int(getattr(row, column)) if column in bools else getattr(row, column)
            for column in columns
        )
        cursor.execute(statement, (parent_id, *values))


def create_app(test_config: dict | None = None) -> Flask:
    app = Flask(__name__)
    app.config.update(
        SECRET_KEY=settings.secret_key,
        SESSION_COOKIE_HTTPONLY=True,
        SESSION_COOKIE_SAMESITE="Lax",
        SESSION_COOKIE_SECURE=settings.secure_cookie,
    )
    if test_config:
        app.config.update(test_config)

    @app.before_request
    def initialize_and_load_user():
        if not app.config.get("TESTING") and not app.extensions.get("pms_database_initialized"):
            db.init_db()
            app.extensions["pms_database_initialized"] = True
        if not session.get("csrf_token"):
            session["csrf_token"] = secrets.token_urlsafe(32)
        g.current_user = None
        user_id = session.get("user_id")
        if user_id:
            g.current_user = db.query_one(
                f"SELECT {USER_FIELDS} FROM users WHERE id=%s", (user_id,)
            )
            if not g.current_user:
                session.clear()
        if request.method in {"POST", "PUT", "PATCH", "DELETE"}:
            submitted = request.form.get("csrf_token", "")
            expected = session.get("csrf_token", "")
            if not submitted or not expected or not hmac.compare_digest(submitted, expected):
                abort(400, description="CSRF 校验失败，请刷新页面后重试")
        if request.endpoint not in {"login", "health", "static"} and not g.current_user:
            return redirect(url_for("login", next=request.full_path))

    @app.context_processor
    def template_context():
        return {"csrf_token": session.get("csrf_token", ""), "view_mode": session.get("view_mode", "auto")}

    @app.errorhandler(400)
    def bad_request(error):
        return render_template("error.html", title="请求无效", message=error.description), 400

    @app.errorhandler(404)
    def not_found(_error):
        return render_template("error.html", title="未找到记录", message="记录不存在或不属于当前用户。"), 404

    @app.errorhandler(403)
    def forbidden(_error):
        return render_template("error.html", title="无权访问", message="仅管理员可操作。"), 403

    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.route("/login", methods=["GET", "POST"])
    def login():
        if g.current_user:
            return redirect(url_for("dashboard"))
        error = None
        if request.method == "POST":
            username = request.form.get("username", "").strip()
            password = request.form.get("password", "")
            row = db.query_one("SELECT * FROM users WHERE username=%s", (username,))
            if row and security.verify_password(password, row["password_hash"]):
                session.clear()
                session["user_id"] = row["id"]
                session["csrf_token"] = secrets.token_urlsafe(32)
                target = request.args.get("next", "")
                parsed_target = urlsplit(target)
                if (
                    not parsed_target.scheme
                    and not parsed_target.netloc
                    and parsed_target.path.startswith("/")
                    and not parsed_target.path.startswith("//")
                ):
                    return redirect(target)
                return redirect(url_for("dashboard"))
            error = "用户名或密码错误"
        return render_template("login.html", error=error)

    @app.post("/logout")
    def logout():
        session.clear()
        return redirect(url_for("login"))

    @app.get("/view/<mode>")
    def switch_view(mode: str):
        if mode not in {"auto", "desktop", "mobile"}:
            abort(404)
        session["view_mode"] = mode
        referrer = request.referrer or ""
        parsed_referrer = urlsplit(referrer)
        if (
            parsed_referrer.netloc not in {"", request.host}
            or parsed_referrer.scheme not in {"", request.scheme}
        ):
            return redirect(url_for("dashboard"))
        return redirect(referrer or url_for("dashboard"))

    @app.get("/")
    def dashboard():
        user_id = g.current_user["id"]
        today = date.today()
        monday = today - timedelta(days=today.weekday())
        month = today.strftime("%Y-%m")
        counts = {
            "daily": db.query_one("SELECT COUNT(*) AS n FROM daily_plans WHERE user_id=%s", (user_id,))["n"],
            "weekly": db.query_one("SELECT COUNT(*) AS n FROM weekly_reviews WHERE user_id=%s", (user_id,))["n"],
            "monthly": db.query_one("SELECT COUNT(*) AS n FROM monthly_reviews WHERE user_id=%s", (user_id,))["n"],
        }
        today_plan = db.query_one(
            "SELECT * FROM daily_plans WHERE user_id=%s AND plan_date=%s", (user_id, today.isoformat())
        )
        week_review = db.query_one(
            "SELECT * FROM weekly_reviews WHERE user_id=%s AND week_start=%s", (user_id, monday.isoformat())
        )
        month_review = db.query_one(
            "SELECT * FROM monthly_reviews WHERE user_id=%s AND month=%s", (user_id, month)
        )
        recent = {
            "daily": db.query("SELECT * FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC LIMIT 5", (user_id,)),
            "weekly": db.query("SELECT * FROM weekly_reviews WHERE user_id=%s ORDER BY week_start DESC LIMIT 5", (user_id,)),
            "monthly": db.query("SELECT * FROM monthly_reviews WHERE user_id=%s ORDER BY month DESC LIMIT 5", (user_id,)),
        }
        last_week = db.query(
            "SELECT id FROM daily_plans WHERE user_id=%s ORDER BY plan_date DESC LIMIT 7", (user_id,)
        )
        block_total = block_done = 0
        for item in last_week:
            blocks = db.query("SELECT is_done FROM time_blocks WHERE daily_plan_id=%s", (item["id"],))
            block_total += len(blocks)
            block_done += sum(bool(block["is_done"]) for block in blocks)
        return render_template(
            "dashboard.html", today=today.isoformat(), counts=counts, recent=recent,
            today_plan=today_plan, week_review=week_review, month_review=month_review,
            block_total=block_total, block_done=block_done,
        )

    @app.get("/<kind>")
    def list_records(kind: str):
        if kind not in RECORDS:
            abort(404)
        spec = RECORDS[kind]
        rows = db.query(
            f"SELECT * FROM {spec['table']} WHERE user_id=%s ORDER BY {spec['unique']} DESC, id DESC",
            (g.current_user["id"],),
        )
        return render_template(
            "record_list.html", kind=kind, title=spec["title"],
            rows=[_record_data(kind, row) for row in rows],
        )

    @app.route("/<kind>/new", methods=["GET", "POST"])
    @app.route("/<kind>/<int:record_id>/edit", methods=["GET", "POST"])
    def edit_record(kind: str, record_id: int | None = None):
        if kind not in RECORDS:
            abort(404)
        spec = RECORDS[kind]
        row = _row(spec["table"], record_id, g.current_user["id"]) if record_id else None
        if record_id and not row:
            abort(404)
        children = None
        if row:
            children = {
                group_name: db.query(
                    f"SELECT * FROM {table} WHERE {foreign_key}=%s ORDER BY id", (record_id,)
                )
                for group_name, table, foreign_key, _columns, _bools in spec["children"]
            }
        values, groups = _form_state(kind, row, children)
        error = None
        if request.method == "POST":
            try:
                body = _form_payload(kind)
                user_id = g.current_user["id"]
                with db.transaction() as conn, conn.cursor() as cursor:
                    cursor.execute(
                        f"SELECT id FROM {spec['table']} WHERE user_id=%s AND {spec['unique']}=%s AND id<>%s",
                        (user_id, getattr(body, spec["unique"]), record_id or 0),
                    )
                    if cursor.fetchone():
                        raise ValueError("该日期或月份的清单已存在")
                    if record_id:
                        assignments = ", ".join(f"{column}=%s" for column in spec["columns"])
                        field_values = tuple(
                            int(getattr(body, column)) if column in spec["bools"] else getattr(body, column)
                            for column in spec["columns"]
                        )
                        cursor.execute(
                            f"UPDATE {spec['table']} SET {assignments}, updated_at=CURRENT_TIMESTAMP "
                            "WHERE id=%s AND user_id=%s",
                            (*field_values, record_id, user_id),
                        )
                        parent_id = record_id
                    else:
                        columns = ("user_id",) + spec["columns"]
                        values_to_insert = (user_id,) + tuple(
                            int(getattr(body, column)) if column in spec["bools"] else getattr(body, column)
                            for column in spec["columns"]
                        )
                        marks = ", ".join(["%s"] * len(columns))
                        cursor.execute(
                            f"INSERT INTO {spec['table']} ({', '.join(columns)}) VALUES ({marks})",
                            values_to_insert,
                        )
                        parent_id = cursor.lastrowid
                    for group_name, _table, _fk, _columns, _bools in spec["children"]:
                        _replace_children(cursor, kind, group_name, parent_id, getattr(body, group_name))
                flash(f"{spec['title']}已保存。", "success")
                return redirect(url_for("list_records", kind=kind))
            except ValidationError as validation_error:
                error = _validation_message(validation_error)
                values = request.form.to_dict()
                groups = _submitted_groups(kind)
            except ValueError as form_error:
                error = str(form_error)
                values = request.form.to_dict()
                groups = _submitted_groups(kind)
            except pymysql.err.IntegrityError as integrity_error:
                if integrity_error.args and integrity_error.args[0] == 1062:
                    error = "主记录或明细中存在重复值，请检查日期、领域和健康检查模块。"
                    values = request.form.to_dict()
                    groups = _submitted_groups(kind)
                else:
                    raise
        fields = _field_specs(spec["columns"])
        return render_template(
            "record_form.html", kind=kind, title=spec["title"], record_id=record_id,
            fields=fields, values=values, groups=groups, error=error,
        )

    @app.post("/<kind>/<int:record_id>/delete")
    def delete_record(kind: str, record_id: int):
        if kind not in RECORDS:
            abort(404)
        spec = RECORDS[kind]
        if not _row(spec["table"], record_id, g.current_user["id"]):
            abort(404)
        with db.transaction() as conn, conn.cursor() as cursor:
            cursor.execute(
                f"DELETE FROM {spec['table']} WHERE id=%s AND user_id=%s",
                (record_id, g.current_user["id"]),
            )
        flash(f"{spec['title']}已删除。", "success")
        return redirect(url_for("list_records", kind=kind))

    @app.route("/admin/users", methods=["GET", "POST"])
    def admin_users():
        if not g.current_user["is_staff"]:
            abort(403)
        error = None
        if request.method == "POST":
            try:
                body = UserIn.model_validate({
                    "username": request.form.get("username", "").strip(),
                    "password": request.form.get("password", ""),
                    "display_name": request.form.get("display_name", "").strip(),
                    "is_staff": "is_staff" in request.form,
                })
                db.execute(
                    "INSERT INTO users (username, password_hash, display_name, is_staff) "
                    "VALUES (%s, %s, %s, %s)",
                    (body.username, security.hash_password(body.password),
                     body.display_name or body.username, int(body.is_staff)),
                )
                flash("用户已创建。", "success")
                return redirect(url_for("admin_users"))
            except ValidationError as validation_error:
                error = _validation_message(validation_error)
            except pymysql.err.IntegrityError as integrity_error:
                if integrity_error.args and integrity_error.args[0] == 1062:
                    error = "用户名已存在"
                else:
                    raise
        users = db.query(f"SELECT {USER_FIELDS} FROM users ORDER BY id")
        return render_template("admin_users.html", users=users, error=error)

    return app


def _row(table: str, record_id: int, user_id: int) -> dict | None:
    return db.query_one(f"SELECT * FROM {table} WHERE id=%s AND user_id=%s", (record_id, user_id))


app = create_app()

if __name__ == "__main__":
    app.run(host="127.0.0.1", port=settings.server_port, debug=True)
