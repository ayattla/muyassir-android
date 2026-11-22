import importlib, os, sys, tempfile
import pytest
from fastapi.testclient import TestClient


@pytest.fixture()
def client(monkeypatch):
    d = tempfile.mkdtemp()
    monkeypatch.setenv("MUYASSIR_DB", os.path.join(d, "t.db")); monkeypatch.setenv("MUYASSIR_ADMIN_PASSWORD", "admin-secret-123")
    sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
    import app as m; importlib.reload(m)
    return TestClient(m.app)


def token(c):
    r = c.post("/api/v1/devices", json={"platform": "android"}); assert r.status_code == 200
    return {"Authorization": "Bearer " + r.json()["token"]}


QUIZ = {"title": "اختبار العواصم", "minutes": 0, "questions": [
    {"text": "عاصمة السعودية؟", "type": "choice", "options": ["جدة", "الرياض"], "answer": "الرياض", "mark": 2},
    {"text": "الشمس نجم", "type": "truefalse", "answer": "صح"},
    {"text": "الماء يغلي عند ___", "type": "blank", "answer": "100"},
    {"text": "رتّب الأعداد", "type": "order", "options": ["3", "1", "2"], "answer": ["1", "2", "3"]}]}


def test_publish_take_submit_and_results(client):
    h = token(client)
    r = client.post("/api/v1/activities", json=QUIZ, headers=h); assert r.status_code == 200
    code = r.json()["code"]; assert r.json()["url"].endswith("/q/" + code)
    page = client.get("/q/" + code); assert page.status_code == 200 and "اختبار العواصم" in page.text and "اكتب اسمك" in page.text
    form = {"name": "سارة", "q0": "الرياض", "q1": "صح", "q2": " 100 ", "q3_0": "1", "q3_1": "2", "q3_2": "3"}
    done = client.post("/q/" + code, data=form); assert "درجتك 5 من 5" in done.text
    client.post("/q/" + code, data={"name": "محمد", "q0": "جدة", "q1": "خطأ", "q2": "90"})
    res = client.get(f"/api/v1/activities/{code}/results", headers=h).json()
    assert [a["name"] for a in res["attempts"]] == ["سارة", "محمد"]
    assert res["per_question"][0]["correct"] == 1 and res["average"] == 2.5


def test_only_owner_reads_results_and_name_required(client):
    h1, h2 = token(client), token(client)
    code = client.post("/api/v1/activities", json=QUIZ, headers=h1).json()["code"]
    assert client.get(f"/api/v1/activities/{code}/results", headers=h2).status_code == 404
    assert "اكتب اسمك" in client.post("/q/" + code, data={"name": " "}).text
    assert client.get("/api/v1/activities/X/results").status_code == 401


def test_closed_activity_and_escaping(client):
    h = token(client)
    q = dict(QUIZ, title="<script>alert(1)</script>")
    code = client.post("/api/v1/activities", json=q, headers=h).json()["code"]
    assert "<script>alert(1)" not in client.get("/q/" + code).text
    client.post(f"/api/v1/activities/{code}/close", headers=h)
    assert "انتهى" in client.get("/q/" + code).text


def test_validation(client):
    h = token(client)
    assert client.post("/api/v1/activities", json={"title": "", "questions": []}, headers=h).status_code == 422
    assert client.post("/api/v1/devices", json={"platform": "web"}).status_code == 422


def test_sync_roundtrip_and_conflict(client):
    h1, h2 = token(client), token(client)
    code = "a" * 24
    assert client.put("/api/v1/sync", json={"code": code, "blob": "TVlTQjEAAAAA"}, headers=h1).status_code == 200
    assert client.get("/api/v1/sync/" + code, headers=h2).json()["blob"] == "TVlTQjEAAAAA"
    assert client.put("/api/v1/sync", json={"code": code, "blob": "TVlTQjEBBBBB"}, headers=h2).status_code == 409


def test_delete_account_removes_everything(client):
    h = token(client)
    code = client.post("/api/v1/activities", json=QUIZ, headers=h).json()["code"]
    client.post("/q/" + code, data={"name": "سارة", "q0": "الرياض"})
    assert client.delete("/api/v1/devices/me", headers=h).json()["deleted"]
    assert client.get("/q/" + code).status_code == 404
    assert client.get(f"/api/v1/activities/{code}/results", headers=h).status_code == 401


def test_admin_and_rate_limit(client):
    assert client.get("/api/v1/admin/stats").status_code == 401
    s = client.get("/api/v1/admin/stats", headers={"Authorization": "Bearer admin-secret-123"}).json()
    assert "devices" in s and "recent_audit" in s
    codes = [client.post("/api/v1/devices", json={"platform": "ios"}).status_code for _ in range(40)]
    assert 429 in codes
