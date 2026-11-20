from app import create_app
import app as monolith


def test_login_rejects_invalid_csrf_token(monkeypatch):
    client = create_app({"TESTING": True}).test_client()
    response = client.post("/login", data={"username": "admin", "password": "password"})
    assert response.status_code == 400
    assert b"CSRF" in response.data


def test_private_pages_redirect_to_login(monkeypatch):
    client = create_app({"TESTING": True}).test_client()
    response = client.get("/daily")
    assert response.status_code == 302
    assert response.headers["Location"].endswith("/login?next=/daily?")


def test_daily_weekly_monthly_forms_render(monkeypatch):
    user = {
        "id": 4,
        "username": "admin",
        "display_name": "Admin",
        "is_staff": True,
        "created_at": "2026-01-01 00:00:00",
    }

    def query_one(sql, args=()):
        return user if "FROM users" in sql else None

    monkeypatch.setattr(monolith.db, "query_one", query_one)
    monkeypatch.setattr(monolith.db, "query", lambda sql, args=(): [])
    client = create_app({"TESTING": True}).test_client()
    with client.session_transaction() as session:
        session["user_id"] = 4
        session["csrf_token"] = "test-token"

    for path in ("/daily/new", "/weekly/new", "/monthly/new"):
        response = client.get(path)
        assert response.status_code == 200
        assert b"<form" in response.data


def test_record_edit_does_not_reveal_another_users_data(monkeypatch):
    user = {
        "id": 4,
        "username": "admin",
        "display_name": "Admin",
        "is_staff": False,
        "created_at": "2026-01-01 00:00:00",
    }

    def query_one(sql, args=()):
        return user if "FROM users" in sql else None

    monkeypatch.setattr(monolith.db, "query_one", query_one)
    client = create_app({"TESTING": True}).test_client()
    with client.session_transaction() as session:
        session["user_id"] = 4
        session["csrf_token"] = "test-token"

    response = client.get("/daily/99/edit")
    assert response.status_code == 404
    assert "记录不存在".encode() in response.data


def test_view_switch_rejects_external_referrer(monkeypatch):
    user = {
        "id": 4,
        "username": "admin",
        "display_name": "Admin",
        "is_staff": False,
        "created_at": "2026-01-01 00:00:00",
    }
    monkeypatch.setattr(monolith.db, "query_one", lambda sql, args=(): user)
    client = create_app({"TESTING": True}).test_client()
    with client.session_transaction() as session:
        session["user_id"] = 4
        session["csrf_token"] = "test-token"

    response = client.get("/view/mobile", headers={"Referer": "https://evil.example/"})
    assert response.status_code == 302
    assert response.headers["Location"] == "/"


def test_login_uses_bcrypt_and_rotates_session(monkeypatch):
    client = create_app({"TESTING": True}).test_client()
    page = client.get("/login")
    import re

    csrf_token = re.search(rb'name="csrf_token" value="([^"]+)"', page.data).group(1).decode()
    assert page.status_code == 200
    monkeypatch.setattr(
        monolith.db,
        "query_one",
        lambda sql, args=(): {
            "id": 4,
            "username": "admin",
            "display_name": "Admin",
            "is_staff": True,
            "password_hash": "hash",
        },
    )
    monkeypatch.setattr(monolith.security, "verify_password", lambda password, hashed: password == "ok")
    response = client.post(
        "/login",
        data={"csrf_token": csrf_token, "username": "admin", "password": "ok"},
    )
    assert response.status_code == 302
    assert response.headers["Location"].endswith("/")
