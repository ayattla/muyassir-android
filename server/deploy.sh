#!/bin/bash
# Deploys the Muyassir server on the ayattla.com Hostinger VPS as its own service (port 8094, path /muyassir/).
# Safe to re-run: it updates app.py and keeps the database and the admin password.
# It never touches /opt/ayattla. nginx is patched in the vhost file AND the CloudPanel template,
# with backups, and both are restored if `nginx -t` fails.
set -euo pipefail
REPO=https://raw.githubusercontent.com/ayattla/muyassir-android/main/server
TS=$(date +%Y%m%d-%H%M%S); mkdir -p /root/dep

# 1. User, folders, code
id muyassir >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin -d /var/lib/muyassir muyassir
mkdir -p /opt/muyassir /var/lib/muyassir /root/backups/muyassir
[ -f /opt/muyassir/app.py ] && cp /opt/muyassir/app.py /root/dep/muyassir-app.$TS.py
curl -fsSL "$REPO/app.py" -o /opt/muyassir/app.py.new && mv /opt/muyassir/app.py.new /opt/muyassir/app.py
[ -x /opt/muyassir/venv/bin/python ] || python3 -m venv /opt/muyassir/venv
/opt/muyassir/venv/bin/pip install -q --upgrade pip >/dev/null
/opt/muyassir/venv/bin/pip install -q "fastapi>=0.110" "uvicorn>=0.29" python-multipart
chown -R muyassir: /var/lib/muyassir; chmod 750 /var/lib/muyassir; chown -R root: /opt/muyassir

# 2. Environment (the admin password is generated once and stays only in this root-only file)
if [ ! -f /etc/muyassir.env ]; then
  umask 077
  printf 'MUYASSIR_DB=/var/lib/muyassir/muyassir.db\nMUYASSIR_BASE=https://ayattla.com/muyassir\nMUYASSIR_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)" > /etc/muyassir.env
fi
chmod 600 /etc/muyassir.env

# 3. Service
cat > /etc/systemd/system/muyassir.service <<'EOF'
[Unit]
Description=Muyassir API (activities, sync)
After=network.target

[Service]
User=muyassir
Group=muyassir
WorkingDirectory=/opt/muyassir
EnvironmentFile=/etc/muyassir.env
ExecStart=/opt/muyassir/venv/bin/uvicorn app:app --host 127.0.0.1 --port 8094 --workers 1 --proxy-headers
Restart=always
RestartSec=3
MemoryMax=400M
NoNewPrivileges=true
ProtectSystem=strict
ReadWritePaths=/var/lib/muyassir
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload; systemctl enable -q muyassir; systemctl restart muyassir
for i in $(seq 1 40); do curl -sf http://127.0.0.1:8094/health >/dev/null && break; sleep 0.5; done
curl -sf http://127.0.0.1:8094/health >/dev/null || { journalctl -u muyassir -n 30 --no-pager; echo "MUYASSIR: service did not start"; exit 1; }

# 4. nginx: vhost file + CloudPanel template, same block after the app marker
python3 - "$TS" <<'EOF'
import sqlite3, shutil, subprocess, sys
ts = sys.argv[1]
F = "/etc/nginx/sites-enabled/ayattla.com.conf"; DB = "/home/clp/htdocs/app/data/db.sq3"
M = "  # --- Ayattla app ---"
B = """  location ^~ /muyassir/ {
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 12m;
    proxy_pass http://127.0.0.1:8094/;
  }
"""
def patch(t):
    if "location ^~ /muyassir/" in t: return t
    assert t.count(M) >= 1, "marker missing"
    return t.replace(M, M + "\n" + B, 1)
shutil.copyfile(F, f"/root/dep/vhost.{ts}.bak"); shutil.copyfile(DB, f"/root/dep/db.sq3.{ts}.bak")
cur = open(F).read(); new = patch(cur)
assert len(new) >= len(cur) > 1000
open(F, "w").write(new)
c = sqlite3.connect(DB)
t = c.execute("select vhost_template from site where domain_name='ayattla.com'").fetchone()[0]
c.execute("update site set vhost_template=? where domain_name='ayattla.com'", (patch(t),)); c.commit(); c.close()
r = subprocess.run(["nginx", "-t"], capture_output=True, text=True)
if r.returncode != 0:
    shutil.copyfile(f"/root/dep/vhost.{ts}.bak", F); shutil.copyfile(f"/root/dep/db.sq3.{ts}.bak", DB)
    print("MUYASSIR: nginx -t FAILED, restored:", r.stderr[-400:]); sys.exit(1)
subprocess.run(["systemctl", "reload", "nginx"], check=True)
print("nginx ok", "(already there)" if new == cur else "(added /muyassir/)")
EOF

# 5. Daily database backup (14 kept) and daily retention cleanup
cat > /usr/local/bin/muyassir-daily <<'EOF'
#!/bin/bash
set -e
. /etc/muyassir.env
D=/root/backups/muyassir; mkdir -p $D
python3 -c "import sqlite3,sys; s=sqlite3.connect('$MUYASSIR_DB'); d=sqlite3.connect(sys.argv[1]); s.backup(d)" "$D/muyassir-$(date +%F).db"
ls -1t $D/muyassir-*.db | tail -n +15 | xargs -r rm -f
curl -sf -X POST -H "Authorization: Bearer $MUYASSIR_ADMIN_PASSWORD" http://127.0.0.1:8094/api/v1/admin/cleanup >/dev/null
EOF
chmod 700 /usr/local/bin/muyassir-daily
echo "17 3 * * * root /usr/local/bin/muyassir-daily >> /var/log/muyassir-daily.log 2>&1" > /etc/cron.d/muyassir
/usr/local/bin/muyassir-daily

echo "MUYASSIR DEPLOYED $TS local=$(curl -s http://127.0.0.1:8094/health)"
