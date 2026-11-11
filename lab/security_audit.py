#!/usr/bin/env python3
"""
Pre-exposure security audit of a running NEXUS NFV stack (stdlib only).

  python lab/security_audit.py                                   # audits http://localhost:3000 (the tunnel origin)
  NEXUS_BASE=https://<name>.trycloudflare.com python lab/security_audit.py

Checks: every OpenAPI operation rejects anonymous calls, no secret from .env appears in the public bundle,
only the reverse proxy is published, actuator exposure is minimal, password hashes are BCrypt, and the
backend contains no shell-execution code path. Exit code = number of failed checks.
"""
import json
import os
import re
import ssl
import subprocess
import sys
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.environ.get("NEXUS_BASE", "http://localhost:3000").rstrip("/")
CTX = ssl.create_default_context()
results = []


def check(name, ok, detail=""):
    results.append(ok)
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" – {detail}" if detail else ""))


def request(method, path, body=None, headers=None):
    req = urllib.request.Request(BASE + path, data=body, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=20, context=CTX) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def env_secrets():
    vals = {}
    p = os.path.join(ROOT, ".env")
    if os.path.exists(p):
        for line in open(p, encoding="utf-8"):
            if "=" in line and not line.startswith("#"):
                k, v = line.strip().split("=", 1)
                if any(x in k for x in ("PASSWORD", "SECRET", "TOKEN", "KEY")) and len(v) >= 12:
                    vals[k] = v
    return vals


# 1. every documented operation requires authentication -------------------------------------------
status, spec = request("GET", "/v3/api-docs")
paths = json.loads(spec).get("paths", {}) if status == 200 else {}
check("OpenAPI document reachable for enumeration", status == 200, f"{len(paths)} paths")
open_ops = []
total = 0
for path, ops in paths.items():
    concrete = re.sub(r"\{[^}]+\}", "1", path)
    for method in ops:
        if method.upper() not in ("GET", "POST", "PUT", "PATCH", "DELETE"):
            continue
        if path == "/api/v1/auth/login":
            continue
        total += 1
        body = b"{}" if method.upper() in ("POST", "PUT", "PATCH") else None
        code, _ = request(method.upper(), concrete, body, {"Content-Type": "application/json"})
        if code not in (401, 403):
            open_ops.append(f"{method.upper()} {path} -> {code}")
check(f"all {total} API operations reject anonymous requests (401/403)", not open_ops, "; ".join(open_ops[:5]))

code, _ = request("GET", "/api/v1/vnfs", headers={"Authorization": "Bearer forged.jwt.token"})
check("forged bearer token rejected", code == 401, f"HTTP {code}")

# 2. actuator exposure -----------------------------------------------------------------------------
code, body = request("GET", "/actuator/health")
check("public health endpoint returns status only", code == 200 and json.loads(body) == {"status": "UP"}, body.decode()[:60])
for p in ("/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump"):
    code, body = request("GET", p)
    leaked = code == 200 and body[:1] in (b"{", b"[") and b"<!doctype" not in body[:30].lower()
    check(f"{p} not exposed", not leaked, f"HTTP {code}")

# 3. no secrets in the public frontend bundle -----------------------------------------------------
code, index = request("GET", "/")
assets = re.findall(rb'(/assets/[\w.-]+\.(?:js|css))', index)
bundle = index
for a in set(assets):
    bundle += request("GET", a.decode())[1]
# lazily loaded chunks referenced from the entry chunk
for a in set(re.findall(rb'assets/[\w.-]+\.js', bundle)):
    bundle += request("GET", "/" + a.decode())[1]
leaks = [k for k, v in env_secrets().items() if v.encode() in bundle]
check("no .env secret appears in served HTML/JS/CSS", not leaks, ", ".join(leaks) or f"{len(bundle) // 1024} KB scanned")
check("no private keys in served bundle", b"PRIVATE KEY" not in bundle)

# 4. only the reverse proxy is published on the host ----------------------------------------------
ps = subprocess.run(["docker", "ps", "--format", "{{.Names}}|{{.Ports}}"], capture_output=True, text=True).stdout
published = {}
for line in ps.splitlines():
    name, _, ports = line.partition("|")
    for m in re.finditer(r"(\d+\.\d+\.\d+\.\d+|\[::\]):(\d+)->(\d+)/tcp", ports):
        published.setdefault(name, set()).add(f"{m.group(1)}:{m.group(2)}")
public = {n: p for n, p in published.items() if any(not x.startswith("127.0.0.1") for x in p)}
ours = {n: p for n, p in public.items() if n.startswith("nexus-")}
check("only the NEXUS reverse proxy listens on non-loopback host ports", set(ours) <= {"nexus-nfv-frontend-1", "nexus-nfv-caddy-1"},
      json.dumps({k: sorted(v) for k, v in ours.items()}))
others = sorted(n for n in public if not n.startswith("nexus-"))
if others:
    print(f"[INFO] containers of other projects publish host ports (not reachable through the tunnel): {', '.join(others)}")
for svc in ("mysql", "docker-proxy"):
    exposed = [n for n in published if svc in n]
    check(f"{svc} has no published host port", not exposed)
vnf_exposed = [n for n in published if n.startswith("nexus-vnf-")]
check("firewall VNF agent (9443) and NETCONF (830) not published", not vnf_exposed)

# 5. credentials at rest ---------------------------------------------------------------------------
env = env_secrets()
root_pw = env.get("MYSQL_ROOT_PASSWORD")
if root_pw:
    q = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={root_pw}", "nexus-nfv-mysql-1", "mysql", "-uroot", "-N", "-e",
                        "SELECT COUNT(*), SUM(password_hash LIKE '$2a$12$%') FROM nexus_nfv.users"],
                       capture_output=True, text=True).stdout.split()
    check("all user passwords stored as BCrypt (cost 12)", len(q) == 2 and q[0] == q[1], f"{q[1] if len(q) == 2 else '?'} / {q[0] if q else '?'} users")
    q = subprocess.run(["docker", "exec", "-e", f"MYSQL_PWD={root_pw}", "nexus-nfv-mysql-1", "mysql", "-uroot", "-N", "-e",
                        "SELECT COUNT(*), SUM(password_enc LIKE 'v1:%') FROM nexus_nfv.netconf_devices"],
                       capture_output=True, text=True).stdout.split()
    check("NETCONF credentials encrypted at rest (AES-GCM)", len(q) == 2 and (q[0] == "0" or q[0] == q[1]), f"{q}")
admin_pw = env.get("NEXUS_ADMIN_PASSWORD", "")
check("admin password is strong (>= 16 chars, mixed classes)", len(admin_pw) >= 16 and all(
    re.search(p, admin_pw) for p in (r"[A-Z]", r"[a-z]", r"\d", r"[^A-Za-z0-9]")))

# 6. no shell execution in the control plane -------------------------------------------------------
offenders = []
for dirpath, _, files in os.walk(os.path.join(ROOT, "backend", "src", "main")):
    for f in files:
        if f.endswith(".java"):
            src = open(os.path.join(dirpath, f), encoding="utf-8").read()
            if re.search(r"Runtime\.getRuntime\(\)\.exec|new ProcessBuilder", src):
                offenders.append(f)
check("backend has no shell/process execution", not offenders, ", ".join(offenders))
agent = open(os.path.join(ROOT, "vnf-firewall", "agent", "nexus_agent.py"), encoding="utf-8").read()
check("VNF agent never uses a shell", "shell=True" not in agent and "os.system" not in agent)

print(f"\n{sum(results)}/{len(results)} security checks passed")
sys.exit(len(results) - sum(results))
