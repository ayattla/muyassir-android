# نشر خادم مُيسّر على خادم Hostinger (بعد موافقة صاحب الخادم)

الخدمة مستقلة عن موقع آيات تتلى: مجلد ومستخدم وقاعدة بيانات وخدمة systemd خاصة بها، ولا تلمس `/opt/ayattla`.

1. `mkdir -p /opt/muyassir /var/lib/muyassir && useradd -r -s /usr/sbin/nologin muyassir` ثم انسخ `server/app.py` إلى `/opt/muyassir`.
2. `python3 -m venv /opt/muyassir/venv && /opt/muyassir/venv/bin/pip install fastapi uvicorn python-multipart`
3. ملف `/etc/muyassir.env` (صلاحية 600): `MUYASSIR_DB=/var/lib/muyassir/muyassir.db`، `MUYASSIR_BASE=https://ayattla.com/muyassir`، `MUYASSIR_ADMIN_PASSWORD=<كلمة قوية>`.
4. خدمة systemd `muyassir` تشغّل `uvicorn app:app --host 127.0.0.1 --port 8094 --workers 1` بالمستخدم `muyassir`.
5. nginx: `location /muyassir/ { proxy_pass http://127.0.0.1:8094/; proxy_set_header X-Real-IP $remote_addr; client_max_body_size 12m; }` في ملف الموقع **وقالب CloudPanel** معًا، ثم `nginx -t` قبل `reload` (قاعدة المستودع ayattla-site).
6. النسخ الاحتياطي: أضف `/var/lib/muyassir/muyassir.db` إلى `ayattla-backup.sh` (نسخة sqlite backup).
7. التنظيف اليومي: `curl -X POST -H "Authorization: Bearer $PW" http://127.0.0.1:8094/api/v1/admin/cleanup` في cron.
8. الفحص: `curl https://ayattla.com/muyassir/health` يعيد `{"ok":true}`.

## حالة النشر
- نُشر في 22 نوفمبر 2026 (05:16 UTC) بموافقة صاحب الخادم، عبر `server/deploy.sh` (نسخة احتياطية `20261122-051652` في `/root/dep`).
- فحص حي عبر Cloudflare: `/muyassir/health` يعيد `{"ok":true}`؛ تسجيل جهاز ← نشر نشاط ← صفحة الطالب ← إرسال (1 من 1) ← النتائج ← حذف النشاط والجهاز، كلها 200؛ لوحة الإدارة دون كلمة مرور 401؛ الرئيسية و`/tools/` و`/blog/` بقيت 200.
- كلمة مرور الإدارة في `/etc/muyassir.env` على الخادم فقط (صلاحية root).
- نسخة يومية من القاعدة في `/root/backups/muyassir` (آخر 14) وتنظيف يومي، عبر `/etc/cron.d/muyassir`.
