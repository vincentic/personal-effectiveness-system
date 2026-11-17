"""PMS REST API：FastAPI 版（纯 API，与 Spring Boot / Gin 版共用数据库与 Token 契约）。"""
from __future__ import annotations

from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from . import db, security
from .config import settings
from .schemas import LoginIn, ProjectIn, TaskIn, UserIn

PROJECT_SELECT = """
SELECT p.*, u.username AS owner_name,
       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id) AS task_count,
       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.status = 'done') AS done_task_count
FROM projects p JOIN users u ON u.id = p.owner_id
"""
TASK_SELECT = """
SELECT t.*, a.username AS assignee_name
FROM tasks t LEFT JOIN users a ON a.id = t.assignee_id
"""
TASK_ORDER = """
ORDER BY FIELD(t.status, 'todo', 'doing', 'done'),
         FIELD(t.priority, 'high', 'medium', 'low'),
         t.due_date IS NULL, t.due_date, t.id
"""
USER_FIELDS = "id, username, display_name, is_staff, created_at"


@asynccontextmanager
async def lifespan(app: FastAPI):
    db.init_db()
    yield


app = FastAPI(
    title="PMS API",
    version="1.0.0",
    description="项目管理系统 REST API（FastAPI 版）。契约见 db/API.md。",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[o.strip() for o in settings.cors_origins.split(",")],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
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


def user_out(row: dict | None) -> dict | None:
    if not row:
        return None
    return {k: row.get(k) for k in ["id", "username", "display_name", "is_staff", "created_at"]}


def project_row(pid: int) -> dict | None:
    return db.query_one(PROJECT_SELECT + " WHERE p.id = %s", (pid,))


def project_out(row: dict | None) -> dict | None:
    if not row:
        return None
    out = dict(row)
    total = int(out.get("task_count") or 0)
    done = int(out.get("done_task_count") or 0)
    out["task_count"] = total
    out["done_task_count"] = done
    out["progress"] = round(done * 100 / total) if total else 0
    out["member_ids"] = [
        r["user_id"] for r in db.query(
            "SELECT user_id FROM project_members WHERE project_id=%s ORDER BY user_id",
            (out["id"],),
        )
    ]
    return out


def task_out(row: dict | None) -> dict | None:
    return dict(row) if row else None


def valid_ids(ids: list[int]) -> list[int]:
    unique = list(dict.fromkeys(int(i) for i in ids))
    if not unique:
        return []
    marks = ",".join(["%s"] * len(unique))
    found = {r["id"] for r in db.query(f"SELECT id FROM users WHERE id IN ({marks})", unique)}
    if len(found) != len(unique):
        raise HTTPException(status_code=400, detail="包含不存在的用户")
    return unique


def set_members(pid: int, ids: list[int]) -> None:
    db.execute("DELETE FROM project_members WHERE project_id=%s", (pid,))
    for uid in ids:
        db.execute("INSERT IGNORE INTO project_members (project_id, user_id) VALUES (%s, %s)", (pid, uid))


def owner_check(row: dict, user: dict) -> None:
    if row["owner_id"] != user["id"]:
        raise HTTPException(status_code=403, detail="仅负责人可操作")


def not_found(name: str = "资源") -> None:
    raise HTTPException(status_code=404, detail=f"资源不存在")


@app.get("/", include_in_schema=False)
def root():
    return RedirectResponse(url="/docs")


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


@app.get("/api/dashboard", tags=["dashboard"])
def dashboard(user: dict = Depends(current_user)):
    counts = db.query_one("""
        SELECT (SELECT COUNT(*) FROM projects) AS project_count,
               (SELECT COUNT(*) FROM tasks) AS task_count,
               (SELECT COUNT(*) FROM tasks WHERE status='todo') AS todo_count,
               (SELECT COUNT(*) FROM tasks WHERE status='doing') AS doing_count,
               (SELECT COUNT(*) FROM tasks WHERE status='done') AS done_count
    """)
    my_projects = db.query(
        PROJECT_SELECT + " WHERE p.owner_id=%s ORDER BY p.created_at DESC, p.id DESC LIMIT 5",
        (user["id"],),
    )
    my_tasks = db.query(
        TASK_SELECT + " WHERE t.assignee_id=%s AND t.status<>'done' "
        + TASK_ORDER + " LIMIT 10",
        (user["id"],),
    )
    return {**counts, "my_projects": [project_out(r) for r in my_projects],
            "my_tasks": [task_out(r) for r in my_tasks]}


@app.get("/api/projects", tags=["projects"])
def list_projects(status: str = "", q: str = "", user: dict = Depends(current_user)):
    rows = db.query(
        PROJECT_SELECT + " WHERE (%s = '' OR p.status = %s) "
        "AND (%s = '' OR p.name LIKE CONCAT('%%', %s, '%%')) "
        "ORDER BY p.created_at DESC, p.id DESC",
        (status, status, q, q),
    )
    return [project_out(r) for r in rows]


@app.post("/api/projects", status_code=201, tags=["projects"])
def create_project(body: ProjectIn, user: dict = Depends(current_user)):
    ids = valid_ids(body.member_ids)
    pid = db.execute(
        "INSERT INTO projects (name, description, status, start_date, end_date, owner_id) "
        "VALUES (%s, %s, %s, %s, %s, %s)",
        (body.name, body.description, body.status, body.start_date, body.end_date, user["id"]),
    )
    set_members(pid, ids)
    return project_out(project_row(pid))


@app.get("/api/projects/{pid}", tags=["projects"])
def get_project(pid: int, user: dict = Depends(current_user)):
    row = project_row(pid)
    if not row:
        not_found("项目")
    out = project_out(row)
    out["tasks"] = [task_out(r) for r in db.query(TASK_SELECT + " WHERE t.project_id=%s " + TASK_ORDER, (pid,))]
    return out


@app.put("/api/projects/{pid}", tags=["projects"])
def update_project(pid: int, body: ProjectIn, user: dict = Depends(current_user)):
    row = project_row(pid)
    if not row:
        not_found("项目")
    owner_check(row, user)
    ids = valid_ids(body.member_ids)
    db.execute(
        "UPDATE projects SET name=%s, description=%s, status=%s, start_date=%s, end_date=%s, "
        "updated_at=CURRENT_TIMESTAMP WHERE id=%s",
        (body.name, body.description, body.status, body.start_date, body.end_date, pid),
    )
    set_members(pid, ids)
    return project_out(project_row(pid))


@app.delete("/api/projects/{pid}", status_code=204, tags=["projects"])
def delete_project(pid: int, user: dict = Depends(current_user)):
    row = project_row(pid)
    if not row:
        not_found("项目")
    owner_check(row, user)
    db.execute("DELETE FROM projects WHERE id=%s", (pid,))
    return Response(status_code=204)


@app.post("/api/projects/{pid}/tasks", status_code=201, tags=["tasks"])
def create_task(pid: int, body: TaskIn, user: dict = Depends(current_user)):
    if not project_row(pid):
        not_found("项目")
    if body.assignee_id is not None and not db.query_one("SELECT id FROM users WHERE id=%s", (body.assignee_id,)):
        raise HTTPException(status_code=400, detail="执行人不存在")
    tid = db.execute(
        "INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, due_date) "
        "VALUES (%s, %s, %s, %s, %s, %s, %s)",
        (pid, body.title, body.description, body.assignee_id, body.status, body.priority, body.due_date),
    )
    return task_out(db.query_one(TASK_SELECT + " WHERE t.id=%s", (tid,)))


@app.put("/api/tasks/{tid}", tags=["tasks"])
def update_task(tid: int, body: TaskIn, user: dict = Depends(current_user)):
    row = db.query_one(TASK_SELECT + " WHERE t.id=%s", (tid,))
    if not row:
        not_found("任务")
    if body.assignee_id is not None and not db.query_one("SELECT id FROM users WHERE id=%s", (body.assignee_id,)):
        raise HTTPException(status_code=400, detail="执行人不存在")
    db.execute(
        "UPDATE tasks SET title=%s, description=%s, assignee_id=%s, status=%s, priority=%s, due_date=%s, "
        "updated_at=CURRENT_TIMESTAMP WHERE id=%s",
        (body.title, body.description, body.assignee_id, body.status, body.priority, body.due_date, tid),
    )
    return task_out(db.query_one(TASK_SELECT + " WHERE t.id=%s", (tid,)))


@app.delete("/api/tasks/{tid}", status_code=204, tags=["tasks"])
def delete_task(tid: int, user: dict = Depends(current_user)):
    if not db.query_one("SELECT id FROM tasks WHERE id=%s", (tid,)):
        not_found("任务")
    db.execute("DELETE FROM tasks WHERE id=%s", (tid,))
    return Response(status_code=204)


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
