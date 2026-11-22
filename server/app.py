"""مُيسّر — server for the features that cannot live on the device alone.

- Device accounts (no email or personal data): a device registers and receives a secret token.
- Interactive activities: a teacher publishes a quiz; students open a short link (or QR) in any browser,
  type a display name, answer within the time limit, and see their score. The teacher reads attempts and
  per-question results. Only the name the student typed is stored.
- Sync between devices: the app uploads an end-to-end encrypted blob (encrypted on the device with the user's
  password) and downloads it on another device with a sync code. The server cannot read it.
- Admin: usage counters, recent errors and deletion requests, behind a password from the environment.

Stack: FastAPI + SQLite (WAL). Runs behind nginx/Cloudflare at /muyassir. No paid services.
"""
from __future__ import annotations

import hashlib
import hmac
import html
import json
import os
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager
from typing import Literal

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import HTMLResponse, JSONResponse
from pydantic import BaseModel, Field, field_validator, model_validator

DB = os.environ.get("MUYASSIR_DB", "/var/lib/muyassir/muyassir.db")
BASE_URL = os.environ.get("MUYASSIR_BASE", "https://ayattla.com/muyassir")
ADMIN_PASSWORD = os.environ.get("MUYASSIR_ADMIN_PASSWORD", "")
MAX_BLOB = 8 * 1024 * 1024
app = FastAPI(title="Muyassir", docs_url=None, redoc_url=None, openapi_url=None)
from pages import router as site_router  # noqa: E402  public tool pages and sitemap
_lock = threading.Lock()

SCHEMA = """
create table if not exists devices(id integer primary key, token_hash text unique not null, created real not null, last_seen real not null, platform text);
create table if not exists activities(id integer primary key, code text unique not null, device_id integer not null, title text not null,
  body text not null, minutes integer not null default 0, opens real, closes real, created real not null, closed integer not null default 0);
create table if not exists attempts(id integer primary key, activity_id integer not null, name text not null, answers text not null,
  score real not null, total real not null, started real, submitted real not null, ip_hash text);
create table if not exists sync(id integer primary key, code_hash text unique not null, device_id integer not null, blob blob not null, updated real not null);
create table if not exists errors(id integer primary key, at real not null, path text, detail text);
create table if not exists audit(id integer primary key, at real not null, device_id integer, action text not null, detail text);
create index if not exists attempts_activity on attempts(activity_id);
"""


@contextmanager
def db():
    os.makedirs(os.path.dirname(DB) or ".", exist_ok=True)
    c = sqlite3.connect(DB, timeout=10)
    c.row_factory = sqlite3.Row
    try:
        c.execute("pragma journal_mode=wal")
        yield c
        c.commit()
    finally:
        c.close()


with db() as _c:
    _c.executescript(SCHEMA)


def sha(s: str) -> str: return hashlib.sha256(s.encode()).hexdigest()


def audit(c, device_id, action, detail=""):
    c.execute("insert into audit(at, device_id, action, detail) values(?,?,?,?)", (time.time(), device_id, action, detail[:300]))


# ---------- rate limiting: token bucket per client IP and route group ----------
_buckets: dict[tuple[str, str], tuple[float, float]] = {}
# A whole class can share one school IP, so student-facing limits are generous.
LIMITS = {"register": (30, 3600), "write": (120, 3600), "submit": (300, 600), "read": (1200, 600)}


def client_ip(req: Request) -> str:
    return req.headers.get("cf-connecting-ip") or req.headers.get("x-real-ip") or (req.client.host if req.client else "?")


def limit(group: str):
    def dep(req: Request):
        cap, per = LIMITS[group]; key = (client_ip(req), group); now = time.time()
        with _lock:
            tokens, at = _buckets.get(key, (cap, now))
            tokens = min(cap, tokens + (now - at) * cap / per)
            if tokens < 1:
                raise HTTPException(429, "طلبات كثيرة. حاول بعد قليل.")
            _buckets[key] = (tokens - 1, now)
    return dep


def device(authorization: str = Header(default="")) -> int:
    tok = authorization.removeprefix("Bearer ").strip()
    if len(tok) < 32:
        raise HTTPException(401, "رمز الجهاز مفقود")
    with db() as c:
        r = c.execute("select id from devices where token_hash=?", (sha(tok),)).fetchone()
        if not r:
            raise HTTPException(401, "رمز الجهاز غير صالح")
        c.execute("update devices set last_seen=? where id=?", (time.time(), r["id"]))
        return r["id"]


@app.exception_handler(Exception)
async def on_error(req: Request, exc: Exception):
    with db() as c:
        c.execute("insert into errors(at, path, detail) values(?,?,?)", (time.time(), req.url.path, repr(exc)[:500]))
    return JSONResponse({"detail": "خطأ في الخادم"}, status_code=500)


@app.middleware("http")
async def headers(req: Request, call_next):
    r = await call_next(req)
    r.headers["X-Content-Type-Options"] = "nosniff"
    r.headers["Referrer-Policy"] = "no-referrer"
    r.headers["X-Frame-Options"] = "DENY"
    if r.headers.get("content-type", "").startswith("text/html"):
        r.headers["Content-Security-Policy"] = "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src 'self' data:; form-action 'self'"
    return r


# ---------- devices ----------
class Register(BaseModel):
    platform: Literal["android", "ios"]


@app.post("/api/v1/devices", dependencies=[Depends(limit("register"))])
def register(b: Register):
    tok = secrets.token_urlsafe(32)
    with db() as c:
        c.execute("insert into devices(token_hash, created, last_seen, platform) values(?,?,?,?)", (sha(tok), time.time(), time.time(), b.platform))
    return {"token": tok}


@app.delete("/api/v1/devices/me")
def delete_me(dev: int = Depends(device)):
    """Deletes the device and everything it created (activities, attempts on them, sync data)."""
    with db() as c:
        ids = [r["id"] for r in c.execute("select id from activities where device_id=?", (dev,))]
        for i in ids:
            c.execute("delete from attempts where activity_id=?", (i,))
        c.execute("delete from activities where device_id=?", (dev,))
        c.execute("delete from sync where device_id=?", (dev,))
        c.execute("delete from devices where id=?", (dev,))
        audit(c, dev, "delete_account", f"activities={len(ids)}")
    return {"deleted": True}


# ---------- activities ----------
class Question(BaseModel):
    text: str = Field(min_length=1, max_length=600)
    type: Literal["choice", "truefalse", "blank", "order", "sort", "classify"]
    options: list[str] = Field(default_factory=list, max_length=8)
    items: list[str] = Field(default_factory=list, max_length=20)  # classify: the things to put into the categories in `options`
    answer: str | list[str] = ""
    mark: float = Field(default=1, ge=0, le=100)

    @field_validator("options")
    @classmethod
    def short(cls, v): return [o[:200] for o in v]

    @field_validator("items")
    @classmethod
    def short_items(cls, v): return [o[:200] for o in v]

    @model_validator(mode="after")
    def classify_shape(self):
        if self.type == "classify":
            ans = self.answer if isinstance(self.answer, list) else []
            if len(self.items) < 2 or len(self.options) < 2 or len(ans) != len(self.items) or any(x not in self.options for x in ans):
                raise ValueError("التصنيف يحتاج عنصرين وفئتين على الأقل، وفئة صحيحة لكل عنصر")
        return self


class Activity(BaseModel):
    title: str = Field(min_length=1, max_length=120)
    questions: list[Question] = Field(min_length=1, max_length=100)
    minutes: int = Field(default=0, ge=0, le=300)
    closes_in_days: int = Field(default=7, ge=1, le=60)
    show_answers: bool = True


def new_code(c) -> str:
    alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    while True:
        code = "".join(secrets.choice(alphabet) for _ in range(6))
        if not c.execute("select 1 from activities where code=?", (code,)).fetchone():
            return code


@app.post("/api/v1/activities", dependencies=[Depends(limit("write"))])
def publish(a: Activity, dev: int = Depends(device)):
    with db() as c:
        code = new_code(c); now = time.time()
        c.execute("insert into activities(code, device_id, title, body, minutes, opens, closes, created) values(?,?,?,?,?,?,?,?)",
                  (code, dev, a.title, a.model_dump_json(), a.minutes, now, now + a.closes_in_days * 86400, now))
        audit(c, dev, "publish", code)
    return {"code": code, "url": f"{BASE_URL}/q/{code}"}


def own(c, code: str, dev: int):
    r = c.execute("select * from activities where code=?", (code.upper(),)).fetchone()
    if not r or r["device_id"] != dev:
        raise HTTPException(404, "النشاط غير موجود")
    return r


@app.get("/api/v1/activities/{code}/results", dependencies=[Depends(limit("read"))])
def results(code: str, dev: int = Depends(device)):
    with db() as c:
        a = own(c, code, dev); body = json.loads(a["body"])
        rows = c.execute("select name, answers, score, total, submitted from attempts where activity_id=? order by submitted", (a["id"],)).fetchall()
    per_q = [0] * len(body["questions"])
    for r in rows:
        for i, ok in enumerate(json.loads(r["answers"]).get("correct", [])):
            if ok and i < len(per_q): per_q[i] += 1
    return {"title": a["title"], "closed": bool(a["closed"]) or time.time() > a["closes"], "attempts": [
        {"name": r["name"], "score": r["score"], "total": r["total"], "submitted": r["submitted"]} for r in rows],
        "per_question": [{"text": q["text"], "correct": per_q[i], "of": len(rows)} for i, q in enumerate(body["questions"])],
        "average": round(sum(r["score"] for r in rows) / len(rows), 2) if rows else 0}


@app.post("/api/v1/activities/{code}/close")
def close(code: str, dev: int = Depends(device)):
    with db() as c:
        a = own(c, code, dev); c.execute("update activities set closed=1 where id=?", (a["id"],)); audit(c, dev, "close", code)
    return {"closed": True}


@app.delete("/api/v1/activities/{code}")
def remove(code: str, dev: int = Depends(device)):
    with db() as c:
        a = own(c, code, dev); c.execute("delete from attempts where activity_id=?", (a["id"],)); c.execute("delete from activities where id=?", (a["id"],)); audit(c, dev, "delete", code)
    return {"deleted": True}


def grade(q: dict, given) -> bool:
    ans = q.get("answer")
    norm = lambda s: " ".join(str(s).strip().lower().replace("أ", "ا").replace("إ", "ا").replace("آ", "ا").replace("ة", "ه").replace("ى", "ي").split())
    if q["type"] in ("order", "sort", "classify"):
        return isinstance(given, list) and [norm(x) for x in given] == [norm(x) for x in (ans if isinstance(ans, list) else [])]
    return norm(given) == norm(ans) and norm(ans) != ""


PAGE = """<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="robots" content="noindex"><title>{title}</title><style>
body{{font-family:system-ui,-apple-system,"Segoe UI",Tahoma,sans-serif;background:#F6F7FB;color:#141A2E;margin:0;padding:16px;display:flex;justify-content:center}}
main{{max-width:640px;width:100%}} .card{{background:#fff;border:1px solid #E3E6EF;border-radius:16px;padding:16px;margin:12px 0}}
h1{{font-size:22px;text-align:center}} label{{display:block;padding:10px;border:1px solid #E3E6EF;border-radius:12px;margin:6px 0}}
input[type=text]{{width:100%;box-sizing:border-box;padding:12px;border:1px solid #CBD0DE;border-radius:12px;font-size:16px}}
button{{width:100%;padding:14px;border:0;border-radius:14px;background:#3346D3;color:#fff;font-size:17px;font-weight:600}}
.muted{{color:#5B6178;font-size:14px;text-align:center}} .ok{{color:#23814A}} .bad{{color:#C62F3A}} #t{{font-weight:700}}
</style></head><body><main>{body}</main></body></html>"""


@app.get("/q/{code}", response_class=HTMLResponse, dependencies=[Depends(limit("read"))])
def take(code: str):
    with db() as c:
        a = c.execute("select * from activities where code=?", (code.upper(),)).fetchone()
    if not a:
        return HTMLResponse(PAGE.format(title="غير موجود", body='<div class="card"><h1>النشاط غير موجود</h1><p class="muted">تأكد من الرابط أو الرمز.</p></div>'), 404)
    if a["closed"] or time.time() > a["closes"]:
        return HTMLResponse(PAGE.format(title="مغلق", body=f'<div class="card"><h1>{html.escape(a["title"])}</h1><p class="muted">انتهى وقت هذا النشاط.</p></div>'))
    body = json.loads(a["body"]); e = html.escape
    qs = []
    for i, q in enumerate(body["questions"]):
        inner = ""
        if q["type"] == "choice":
            inner = "".join(f'<label><input type="radio" name="q{i}" value="{e(o)}"> {e(o)}</label>' for o in q["options"])
        elif q["type"] == "truefalse":
            inner = "".join(f'<label><input type="radio" name="q{i}" value="{v}"> {v}</label>' for v in ("صح", "خطأ"))
        elif q["type"] == "blank":
            inner = f'<input type="text" name="q{i}" maxlength="200" placeholder="إجابتك">'
        elif q["type"] == "classify":
            opts = "".join(f'<option value="{e(o)}">{e(o)}</option>' for o in q["options"])
            inner = '<p class="muted">اختر الفئة المناسبة لكل عنصر</p>' + "".join(
                f'<label>{e(it)} <select name="q{i}_{k}"><option value=""></option>{opts}</select></label>' for k, it in enumerate(q.get("items", [])))
        else:
            opts = "".join(f'<option value="{e(o)}">{e(o)}</option>' for o in q["options"])
            inner = "".join(f'<label>{k + 1}) <select name="q{i}_{k}"><option value=""></option>{opts}</select></label>' for k in range(len(q["options"])))
            inner = f'<p class="muted">{"رتّب العناصر" if q["type"] == "order" else "صنّف العناصر"} باختيار العنصر لكل موضع</p>' + inner
        qs.append(f'<div class="card"><b>{i + 1}) {e(q["text"])}</b>{inner}</div>')
    timer = f'<p class="muted">الوقت المتبقي: <span id="t"></span></p><script>var s={a["minutes"] * 60};var f=document.forms[0];setInterval(function(){{s--;document.getElementById("t").textContent=Math.floor(s/60)+":"+("0"+s%60).slice(-2);if(s<=0)f.submit()}},1000)</script>' if a["minutes"] else ""
    form = (f'<h1>{e(a["title"])}</h1><form method="post" action="{e(code.upper())}"><div class="card"><input type="text" name="name" required maxlength="60" placeholder="اكتب اسمك">'
            f'<p class="muted">لا نطلب أي بيانات غير الاسم الذي تكتبه.</p></div><input type="hidden" name="started" value="{time.time():.0f}">{"".join(qs)}<button>إرسال الإجابات</button></form>{timer}')
    return PAGE.format(title=e(a["title"]), body=form)


@app.post("/q/{code}", response_class=HTMLResponse, dependencies=[Depends(limit("submit"))])
async def submit(code: str, req: Request):
    form = await req.form()
    name = str(form.get("name", "")).strip()[:60]
    with db() as c:
        a = c.execute("select * from activities where code=?", (code.upper(),)).fetchone()
        if not a:
            raise HTTPException(404, "النشاط غير موجود")
        if a["closed"] or time.time() > a["closes"]:
            return PAGE.format(title="مغلق", body='<div class="card"><h1>انتهى وقت هذا النشاط</h1></div>')
        if not name:
            return PAGE.format(title="الاسم مطلوب", body='<div class="card"><h1>اكتب اسمك ثم أعد الإرسال</h1></div>')
        body = json.loads(a["body"]); score = 0.0; total = 0.0; correct = []; given_all = []
        for i, q in enumerate(body["questions"]):
            if q["type"] in ("order", "sort", "classify"):
                n = len(q.get("items", [])) if q["type"] == "classify" else len(q["options"])
                given = [str(form.get(f"q{i}_{k}", "")) for k in range(n)]
            else:
                given = str(form.get(f"q{i}", ""))
            ok = grade(q, given); correct.append(ok); given_all.append(given)
            total += q.get("mark", 1); score += q.get("mark", 1) if ok else 0
        late = a["minutes"] and time.time() - float(form.get("started", time.time())) > a["minutes"] * 60 + 30
        c.execute("insert into attempts(activity_id, name, answers, score, total, started, submitted, ip_hash) values(?,?,?,?,?,?,?,?)",
                  (a["id"], name, json.dumps({"given": given_all, "correct": correct, "late": bool(late)}, ensure_ascii=False), score, total,
                   float(form.get("started", 0) or 0), time.time(), sha(client_ip(req))[:16]))
    rows = ""
    if body.get("show_answers", True):
        rows = "".join(f'<p class="{"ok" if ok else "bad"}">{i + 1}) {"✔" if ok else "✘"} {html.escape(q["text"])}</p>' for i, (q, ok) in enumerate(zip(body["questions"], correct)))
    return PAGE.format(title="النتيجة", body=f'<div class="card"><h1>درجتك {score:g} من {total:g}</h1><p class="muted">شكرًا {html.escape(name)}، وصلت إجاباتك للمعلم.</p>{rows}</div>')


# ---------- end-to-end encrypted sync ----------
class SyncPut(BaseModel):
    code: str = Field(min_length=16, max_length=128)
    blob: str = Field(min_length=8)  # base64 of the encrypted backup ("MYSB1"...) made on the device


@app.put("/api/v1/sync", dependencies=[Depends(limit("write"))])
def sync_put(b: SyncPut, dev: int = Depends(device)):
    if len(b.blob) > MAX_BLOB * 4 // 3 + 16:
        raise HTTPException(413, "النسخة أكبر من الحد المسموح (8 م.ب)")
    with db() as c:
        h = sha(b.code); r = c.execute("select device_id from sync where code_hash=?", (h,)).fetchone()
        if r and r["device_id"] != dev:
            raise HTTPException(409, "رمز المزامنة مستخدم")
        c.execute("insert into sync(code_hash, device_id, blob, updated) values(?,?,?,?) on conflict(code_hash) do update set blob=excluded.blob, updated=excluded.updated",
                  (h, dev, b.blob, time.time()))
    return {"saved": True}


@app.get("/api/v1/sync/{code}", dependencies=[Depends(limit("read"))])
def sync_get(code: str, dev: int = Depends(device)):
    with db() as c:
        r = c.execute("select blob, updated from sync where code_hash=?", (sha(code),)).fetchone()
    if not r:
        raise HTTPException(404, "لا توجد نسخة بهذا الرمز")
    return {"blob": r["blob"], "updated": r["updated"]}


# ---------- admin ----------
def admin(authorization: str = Header(default="")):
    pw = authorization.removeprefix("Bearer ").strip()
    if not ADMIN_PASSWORD or not hmac.compare_digest(pw, ADMIN_PASSWORD):
        raise HTTPException(401, "غير مصرح")


@app.get("/api/v1/admin/stats", dependencies=[Depends(admin)])
def stats():
    with db() as c:
        q = lambda s: c.execute(s).fetchone()[0]
        day = time.time() - 86400
        return {"devices": q("select count(*) from devices"), "active_24h": c.execute("select count(*) from devices where last_seen>?", (day,)).fetchone()[0],
                "activities": q("select count(*) from activities"), "attempts": q("select count(*) from attempts"),
                "sync_blobs": q("select count(*) from sync"), "errors_24h": c.execute("select count(*) from errors where at>?", (day,)).fetchone()[0],
                "recent_errors": [dict(r) for r in c.execute("select at, path, detail from errors order by at desc limit 20")],
                "recent_audit": [dict(r) for r in c.execute("select at, device_id, action, detail from audit order by at desc limit 50")]}


@app.post("/api/v1/admin/cleanup", dependencies=[Depends(admin)])
def cleanup():
    """Retention: closed activities older than 90 days and their attempts; sync blobs untouched for 180 days."""
    cut_a, cut_s = time.time() - 90 * 86400, time.time() - 180 * 86400
    with db() as c:
        ids = [r["id"] for r in c.execute("select id from activities where closes<?", (cut_a,))]
        for i in ids: c.execute("delete from attempts where activity_id=?", (i,))
        c.execute("delete from activities where closes<?", (cut_a,))
        n = c.execute("delete from sync where updated<?", (cut_s,)).rowcount
        audit(c, None, "cleanup", f"activities={len(ids)} sync={n}")
    return {"activities": len(ids), "sync": n}


@app.get("/health")
def health(): return {"ok": True}


app.include_router(site_router)
