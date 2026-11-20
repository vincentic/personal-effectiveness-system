from app import create_app
import app as api


def make_client(monkeypatch, *, staff=True):
    monkeypatch.setattr(api.security, "verify_token", lambda token: {"uid": 7} if token == "valid" else None)
    monkeypatch.setattr(
        api.db,
        "query_one",
        lambda sql, args=(): {
            "id": 7,
            "username": "tester",
            "display_name": "Test",
            "is_staff": staff,
            "created_at": "2026-01-01 00:00:00",
        },
    )
    return create_app({"TESTING": True}).test_client()


def test_me_requires_bearer_token(monkeypatch):
    response = make_client(monkeypatch).get("/api/me")
    assert response.status_code == 401
    assert response.json == {"detail": "未认证或 Token 已过期"}


def test_me_serializes_staff_as_boolean_without_password_hash(monkeypatch):
    client = make_client(monkeypatch)
    response = client.get("/api/me", headers={"Authorization": "Bearer valid"})
    assert response.status_code == 200
    assert response.json["is_staff"] is True
    assert "password_hash" not in response.json


def test_daily_rejects_invalid_date_before_database_write(monkeypatch):
    client = make_client(monkeypatch)
    response = client.post(
        "/api/daily",
        headers={"Authorization": "Bearer valid"},
        json={"plan_date": "2026-13-40"},
    )
    assert response.status_code == 400
    assert response.json["detail"] == "日期格式应为 YYYY-MM-DD"


def test_daily_rejects_malformed_json_with_contract_error(monkeypatch):
    client = make_client(monkeypatch)
    response = client.post(
        "/api/daily",
        headers={"Authorization": "Bearer valid", "Content-Type": "application/json"},
        data="{",
    )
    assert response.status_code == 400
    assert response.json == {"detail": "JSON 请求格式错误"}


def test_daily_detail_is_scoped_to_current_user(monkeypatch):
    client = make_client(monkeypatch)
    user = {
        "id": 7,
        "username": "tester",
        "display_name": "Test",
        "is_staff": True,
        "created_at": "2026-01-01 00:00:00",
    }

    def query_one(sql, args=()):
        return user if "FROM users" in sql else None

    monkeypatch.setattr(api.db, "query_one", query_one)
    response = client.get("/api/daily/99", headers={"Authorization": "Bearer valid"})
    assert response.status_code == 404
    assert response.json == {"detail": "记录不存在"}


def test_cors_preflight_does_not_require_a_token(monkeypatch):
    client = make_client(monkeypatch)
    response = client.options(
        "/api/me",
        headers={
            "Origin": "http://localhost:5173",
            "Access-Control-Request-Method": "GET",
        },
    )
    assert response.status_code == 200
    assert response.headers["Access-Control-Allow-Origin"] == "http://localhost:5173"
