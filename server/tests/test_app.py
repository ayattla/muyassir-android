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
    {"text": "رتب الأعداد", "type": "order", "options": ["3", "1", "2"], "answer": ["1", "2", "3"]}]}


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


def test_classify_activity(client):
    h = token(client)
    bad = {"title": "تصنيف", "questions": [{"text": "صنف", "type": "classify", "items": ["أسد"], "options": ["ثدييات", "طيور"], "answer": ["ثدييات"]}]}
    assert client.post("/api/v1/activities", json=bad, headers=h).status_code == 422
    q = {"title": "تصنيف الحيوانات", "questions": [{"text": "صنف الحيوانات", "type": "classify", "items": ["أسد", "صقر", "حوت"],
         "options": ["ثدييات", "طيور"], "answer": ["ثدييات", "طيور", "ثدييات"]}]}
    code = client.post("/api/v1/activities", json=q, headers=h).json()["code"]
    page = client.get("/q/" + code).text; assert "اختر الفئة المناسبة" in page and "صقر" in page
    done = client.post("/q/" + code, data={"name": "ليان", "q0_0": "ثدييات", "q0_1": "طيور", "q0_2": "ثدييات"}); assert "درجتك 1 من 1" in done.text
    wrong = client.post("/q/" + code, data={"name": "سعد", "q0_0": "ثدييات", "q0_1": "ثدييات", "q0_2": "ثدييات"}); assert "درجتك 0 من 1" in wrong.text


def test_public_tool_pages(client):
    home = client.get("/"); assert home.status_code == 200 and "أداة عربية في تطبيق واحد" in home.text and 'rel="canonical"' in home.text
    assert "/t/salary" in home.text and "SoftwareApplication" in home.text
    t = client.get("/t/settlement"); assert t.status_code == 200 and "التسوية" in t.text and "المصدر الرسمي" in t.text and "BreadcrumbList" in t.text
    assert client.get("/t/nothing").status_code == 404
    sm = client.get("/sitemap.xml"); assert sm.status_code == 200 and sm.text.count("<url>") == 93


def test_school_licence_codes(client):
    adm = {"Authorization": "Bearer " + os.environ["MUYASSIR_ADMIN_PASSWORD"]}
    assert client.post("/api/v1/admin/licenses", json={"school": "مدرسة", "seats": 2}).status_code == 401
    r = client.post("/api/v1/admin/licenses", json={"school": "مدارس الرواد", "seats": 2, "note": "فاتورة 1"}, headers=adm).json()
    a, b = r["codes"]; assert a.startswith("MSR-") and len(a) == 13
    t1, t2 = token(client), token(client)
    assert client.get("/api/v1/entitlement", headers=t1).json()["plan"] == "none"
    assert client.post("/api/v1/redeem", json={"code": "MSR-AAAA-AAAA"}, headers=t1).status_code == 404
    e = client.post("/api/v1/redeem", json={"code": a.lower().replace("-", " ")}, headers=t1).json()
    assert e["plan"] == "school" and e["school"] == "مدارس الرواد"
    assert client.post("/api/v1/redeem", json={"code": a}, headers=t1).status_code == 200      # same device again is fine
    assert client.post("/api/v1/redeem", json={"code": a}, headers=t2).status_code == 409      # another device is not
    assert client.post(f"/api/v1/admin/codes/{a}/release", headers=adm).status_code == 200
    assert client.post("/api/v1/redeem", json={"code": a}, headers=t2).json()["plan"] == "school"
    ls = client.get("/api/v1/admin/licenses", headers=adm).json(); assert ls[0]["used"] == 1 and ls[0]["seats"] == 2
    assert client.post(f"/api/v1/admin/licenses/{r['id']}/revoke", headers=adm).status_code == 200
    assert client.get("/api/v1/entitlement", headers=t2).json()["plan"] == "none"
    assert client.post("/api/v1/redeem", json={"code": b}, headers=t1).status_code == 410
    page = client.get("/admin"); assert page.status_code == 200 and "noindex" in page.text


def test_deleting_device_frees_the_seat(client):
    adm = {"Authorization": "Bearer " + os.environ["MUYASSIR_ADMIN_PASSWORD"]}
    code = client.post("/api/v1/admin/licenses", json={"school": "مدرسة س", "seats": 1}, headers=adm).json()["codes"][0]
    t1, t2 = token(client), token(client)
    client.post("/api/v1/redeem", json={"code": code}, headers=t1)
    assert client.delete("/api/v1/devices/me", headers=t1).status_code == 200
    assert client.post("/api/v1/redeem", json={"code": code}, headers=t2).json()["plan"] == "school"
