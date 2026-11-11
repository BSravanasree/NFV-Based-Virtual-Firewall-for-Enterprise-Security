#!/usr/bin/env python3
"""
End-to-end verification of the NEXUS NFV platform against the running Docker lab.

  python lab/e2e_test.py                      # reads admin credentials from .env
  NEXUS_API=http://localhost:8080/api/v1 NEXUS_ADMIN_PASSWORD=... python lab/e2e_test.py

Drives the public REST API (exactly what the UI does) and checks real traffic from the lab containers.
Exit code = number of failed checks. Cleans up the VNF it creates.
"""
import json
import os
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
API = os.environ.get("NEXUS_API", "http://localhost:8080/api/v1")
VNF_NAME = os.environ.get("E2E_VNF_NAME", "fw-e2e")
SERVER = "10.20.0.10"
RESULTS = []
# Only for rehearsing the HTTPS path locally against a self-signed certificate (never in production).
SSL_CTX = ssl._create_unverified_context() if os.environ.get("NEXUS_API_INSECURE") == "1" else None


def env_file():
    vals = {}
    p = os.path.join(ROOT, ".env")
    if os.path.exists(p):
        for line in open(p, encoding="utf-8"):
            if "=" in line and not line.lstrip().startswith("#"):
                k, v = line.rstrip("\n").split("=", 1)
                vals[k.strip()] = v
    return vals


ENV = env_file()
ADMIN_USER = os.environ.get("NEXUS_ADMIN_USERNAME", ENV.get("NEXUS_ADMIN_USERNAME", "admin"))
ADMIN_PW = os.environ.get("NEXUS_ADMIN_PASSWORD", ENV.get("NEXUS_ADMIN_PASSWORD", ""))
DEMO_PW = os.environ.get("NEXUS_DEMO_USERS_PASSWORD", ENV.get("NEXUS_DEMO_USERS_PASSWORD", ""))


def call(method, path, body=None, token=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=120, context=SSL_CTX) as r:
            content = r.read()
            return r.status, (content if raw else (json.loads(content) if content else None))
    except urllib.error.HTTPError as e:
        content = e.read()
        try:
            return e.code, json.loads(content)
        except ValueError:
            return e.code, content


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok)))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}" + (f" – {detail}" if detail else ""), flush=True)
    return ok


def traffic(container, *cmd):
    r = subprocess.run(["docker", "exec", container, *cmd], capture_output=True, text=True, timeout=30)
    return r.returncode == 0


def login(user, pw):
    s, b = call("POST", "/auth/login", {"username": user, "password": pw})
    if s != 200:
        raise SystemExit(f"login as {user} failed: {s} {b}")
    return b["accessToken"]


def wait_state(token, vid, targets, timeout=120):
    deadline = time.time() + timeout
    v = None
    while time.time() < deadline:
        _, v = call("GET", f"/vnfs/{vid}", token=token)
        if v["state"] in targets:
            return v
        time.sleep(2)
    return v


def add_rule(token, vid, rule):
    s, b = call("POST", f"/firewall/vnfs/{vid}/rules", rule, token)
    return s, b


def main():
    if not ADMIN_PW:
        raise SystemExit("NEXUS_ADMIN_PASSWORD not found (.env or environment)")
    t = login(ADMIN_USER, ADMIN_PW)

    # clean leftovers from a previous run
    _, vnfs = call("GET", "/vnfs", token=t)
    for v in vnfs:
        if v["name"] == VNF_NAME:
            if v["state"] not in ("CREATED", "TERMINATED"):
                call("POST", f"/vnfs/{v['id']}/terminate", token=t)
                wait_state(t, v["id"], {"TERMINATED", "FAILED"})
            call("DELETE", f"/vnfs/{v['id']}", token=t)

    print("== 1. VNF creation & deployment")
    s, v = call("POST", "/vnfs", {"name": VNF_NAME, "vnfType": "FIREWALL", "cpuLimit": 0.5, "memoryLimitMb": 256,
                                  "lanIp": "10.10.0.254", "wanIp": "10.20.0.254", "defaultAction": "DROP"}, t)
    check("create VNF -> CREATED", s == 201 and v["state"] == "CREATED", f"id={v.get('id')}")
    vid = v["id"]
    s, v = call("POST", f"/vnfs/{vid}/deploy", token=t)
    check("deploy accepted -> INSTANTIATING", s == 202 and v["state"] == "INSTANTIATING")
    t0 = time.time()
    v = wait_state(t, vid, {"ACTIVE", "FAILED"})
    check("VNF reaches ACTIVE (confirmed by Docker + agent)", v["state"] == "ACTIVE",
          f"{time.time() - t0:.1f}s, container {str(v.get('externalId'))[:12]}, mgmt {v.get('mgmtIp')}" if v["state"] == "ACTIVE" else v.get("lastError"))
    if v["state"] != "ACTIVE":
        return
    time.sleep(3)
    _, v = call("GET", f"/vnfs/{vid}", token=t)
    check("initial policy pushed (IN_SYNC)", v["syncStatus"] == "IN_SYNC", f"r{v['appliedRevision']}/{v['rulesRevision']}")

    print("== 2. default-deny enforced in the data path")
    check("client1 -> server :80 blocked (no rules, default DROP)", not traffic("nexus-lab-client1", "curl", "-sf", "-m", "3", f"http://{SERVER}/"))

    print("== 3. firewall rules applied to the VNF")
    s, b = add_rule(t, vid, {"name": "allow-web-client1", "priority": 10, "action": "ALLOW", "protocol": "TCP",
                              "sourceCidr": "10.10.0.11", "destinationCidr": SERVER, "destinationPort": "80"})
    check("create allow rule -> enforced", s == 201 and b["sync"]["applied"], b["sync"]["message"] if s == 201 else str(b))
    web_rule = b["rule"]["id"]
    s, b = add_rule(t, vid, {"name": "allow-ping-lan", "priority": 20, "action": "ALLOW", "protocol": "ICMP",
                              "sourceCidr": "10.10.0.0/24"})
    check("create ICMP rule -> enforced", s == 201 and b["sync"]["applied"])
    ping_rule = b["rule"]["id"]
    s, b = add_rule(t, vid, {"name": "deny-ssh", "priority": 5, "action": "DENY", "protocol": "TCP", "destinationPort": "22"})
    check("create deny rule -> enforced", s == 201 and b["sync"]["applied"])
    s, b = add_rule(t, vid, {"name": "bad", "priority": 30, "action": "ALLOW", "protocol": "ICMP", "destinationPort": "80"})
    check("invalid rule rejected (400)", s == 400)

    print("== 4. allowed / blocked traffic through the VNF")
    cases = [
        ("client1 HTTP :80 allowed", True, "nexus-lab-client1", ["curl", "-sf", "-m", "3", f"http://{SERVER}/"]),
        ("client2 HTTP :80 blocked", False, "nexus-lab-client2", ["curl", "-sf", "-m", "3", f"http://{SERVER}/"]),
        ("client1 HTTP :8080 blocked", False, "nexus-lab-client1", ["curl", "-sf", "-m", "3", f"http://{SERVER}:8080/"]),
        ("client1 ICMP allowed", True, "nexus-lab-client1", ["ping", "-c", "1", "-W", "2", SERVER]),
        ("client2 ICMP allowed", True, "nexus-lab-client2", ["ping", "-c", "1", "-W", "2", SERVER]),
        ("client1 TCP :22 blocked (deny-ssh)", False, "nexus-lab-client1", ["nc", "-z", "-w", "2", SERVER, "22"]),
        ("client2 UDP :5353 blocked (default)", False, "nexus-lab-client2", ["sh", "-c", f"echo hi | nc -u -w 2 {SERVER} 5353 | grep -q echo"]),
    ]
    for name, expect, ctr, cmd in cases:
        got = traffic(ctr, *cmd)
        check(name, got == expect, f"observed {'ALLOW' if got else 'BLOCK'}")

    s, b = call("PATCH", f"/firewall/rules/{ping_rule}/enabled", {"enabled": False}, t)
    check("disable ICMP rule -> enforced", s == 200 and b["sync"]["applied"])
    check("client1 ICMP now blocked", not traffic("nexus-lab-client1", "ping", "-c", "1", "-W", "2", SERVER))
    call("PATCH", f"/firewall/rules/{ping_rule}/enabled", {"enabled": True}, t)
    check("client1 ICMP allowed again", traffic("nexus-lab-client1", "ping", "-c", "1", "-W", "2", SERVER))

    s, rt = call("GET", f"/firewall/vnfs/{vid}/runtime", token=t)
    check("runtime ruleset read from VNF (nft list)", s == 200 and f'comment "rule:{web_rule}"' in rt["rendered"],
          f"revision {rt.get('revision')}, source {rt.get('source')}")

    print("== 5. attack simulation -> telemetry -> detection")
    subprocess.run(["docker", "exec", "nexus-lab-client2", "nmap", "-Pn", "-T4", "--max-retries", "0", "-p", "1-120", SERVER],
                   capture_output=True, timeout=120)
    for _ in range(8):
        traffic("nexus-lab-client2", "nc", "-z", "-w", "1", SERVER, "3306")
    print("   waiting for two telemetry polls (counters + NFLOG events)…", flush=True)
    time.sleep(25)
    _, ev = call("GET", "/traffic/events?action=BLOCKED&srcIp=10.10.0.12&size=5", token=t)
    check("blocked NFLOG events collected", ev["totalElements"] > 50, f"{ev['totalElements']} events, e.g. {ev['content'][0]['dstIp']}:{ev['content'][0]['dstPort']}" if ev["content"] else "")
    _, ev = call("GET", "/traffic/events?action=ALLOWED&srcIp=10.10.0.11&size=5", token=t)
    check("allowed connection events collected", ev["totalElements"] > 0, f"{ev['totalElements']} events")
    _, summ = call("GET", "/traffic/summary?minutes=15", token=t)
    check("counters sampled with LAB provenance", summ["samples"] > 0 and "LAB" in summ["dataSources"] and summ["blockedPackets"] > 0,
          f"allowed={summ['allowedPackets']} blocked={summ['blockedPackets']} tcp={summ['tcpPackets']} icmp={summ['icmpPackets']}")
    _, pol = call("GET", f"/firewall/vnfs/{vid}/policy", token=t)
    hits = {r["name"]: r["hitPackets"] for r in pol["rules"]}
    check("per-rule hit counters", hits.get("allow-web-client1", 0) > 0 and hits.get("deny-ssh", 0) > 0, str(hits))
    s, d = call("POST", "/alerts/detect", token=t)
    _, alerts = call("GET", "/alerts?sourceIp=10.10.0.12&size=50", token=t)
    detectors = sorted({a["detector"] for a in alerts["content"]})
    check("port scan detected", "PORT_SCAN" in detectors, ", ".join(detectors))
    check("unauthorized access to sensitive port detected", "UNAUTHORIZED_ACCESS" in detectors)
    check("repeated blocked traffic detected", "REPEATED_BLOCKED" in detectors)

    print("== 6. VNF metrics")
    _, v = call("GET", f"/vnfs/{vid}", token=t)
    check("CPU/memory measured from Docker (LAB)", v.get("metricsSource") == "LAB" and v.get("memoryUsageMb") is not None,
          f"cpu={v.get('cpuPercent')}% mem={v.get('memoryUsageMb')}MiB health={v.get('health')}")

    print("== 7. NETCONF (Netopeer2 inside the VNF)")
    _, devs = call("GET", "/netconf/devices", token=t)
    dev = next((d for d in devs if d.get("vnfId") == vid), None)
    check("NETCONF device auto-registered", dev is not None)
    if dev:
        did = dev["id"]
        s, d = call("POST", f"/netconf/devices/{did}/test", token=t)
        # YANG 1.1 modules are announced through ietf-yang-library, not as individual <hello> capabilities
        check("NETCONF session + capabilities", s == 200 and d["status"] == "CONNECTED"
              and any(c.startswith("urn:ietf:params:netconf:base:1.0") for c in d["capabilities"]),
              f"{len(d.get('capabilities', []))} capabilities, host key {str(d.get('hostKeyFingerprint'))[:24]}…" if s == 200 else str(d))
        s, rc = call("GET", f"/netconf/devices/{did}/running", token=t)
        check("get-config running reflects enforced policy", s == 200 and rc["ruleCount"] == 3, f"{rc.get('ruleCount')} rules")
        s, sy = call("GET", f"/netconf/devices/{did}/sync", token=t)
        check("sync check IN_SYNC", s == 200 and sy["status"] == "IN_SYNC", sy.get("detail"))
        s, r = call("POST", f"/netconf/devices/{did}/validate", token=t)
        check("validate on candidate", s == 200 and r["success"], r.get("message"))
        s, r = call("POST", f"/netconf/devices/{did}/push", token=t)
        check("push (lock/edit/validate/commit)", s == 200 and r["success"], r.get("message"))
        v1 = r.get("version", {}).get("id")
        # change policy via REST, then roll back via NETCONF and verify enforcement changes in the data path
        call("PATCH", f"/firewall/rules/{web_rule}/enabled", {"enabled": False}, t)
        check("client1 HTTP blocked after disabling rule", not traffic("nexus-lab-client1", "curl", "-sf", "-m", "3", f"http://{SERVER}/"))
        s, r = call("POST", f"/netconf/devices/{did}/versions/{v1}/rollback", token=t)
        check("NETCONF rollback committed", s == 200 and r["success"], r.get("message"))
        time.sleep(5)  # agent's datastore bridge polls every 3 s
        check("rolled-back config enforced (client1 HTTP allowed via NETCONF)", traffic("nexus-lab-client1", "curl", "-sf", "-m", "3", f"http://{SERVER}/"))
        s, sy = call("GET", f"/netconf/devices/{did}/sync", token=t)
        check("drift detected vs desired policy", sy["status"] == "DRIFT", sy.get("detail"))
        call("POST", f"/firewall/vnfs/{vid}/apply", token=t)  # restore desired state
        call("PATCH", f"/firewall/rules/{web_rule}/enabled", {"enabled": True}, t)

    print("== 8. lifecycle operations")
    s, v = call("POST", f"/vnfs/{vid}/scale", {"direction": "VERTICAL", "cpuLimit": 1, "memoryLimitMb": 384}, t)
    check("vertical scale", s == 200 and v["memoryLimitMb"] == 384)
    s, v = call("POST", f"/vnfs/{vid}/scale", {"direction": "SCALE_OUT"}, t)
    check("horizontal scale refused in Docker lab (needs OSM)", s == 400)
    call("POST", f"/vnfs/{vid}/restart", token=t)
    v = wait_state(t, vid, {"ACTIVE", "FAILED"})
    time.sleep(2)
    check("restart -> ACTIVE", v["state"] == "ACTIVE")
    check("policy survives restart (client1 HTTP allowed)", traffic("nexus-lab-client1", "curl", "-sf", "-m", "3", f"http://{SERVER}/"))
    check("policy survives restart (client2 HTTP blocked)", not traffic("nexus-lab-client2", "curl", "-sf", "-m", "3", f"http://{SERVER}/"))
    call("POST", f"/vnfs/{vid}/stop", token=t)
    v = wait_state(t, vid, {"STOPPED", "FAILED"})
    check("stop -> STOPPED", v["state"] == "STOPPED")
    check("no forwarding while stopped", not traffic("nexus-lab-client1", "curl", "-sf", "-m", "3", f"http://{SERVER}/"))
    call("POST", f"/vnfs/{vid}/start", token=t)
    v = wait_state(t, vid, {"ACTIVE", "FAILED"})
    time.sleep(2)
    check("start -> ACTIVE and forwarding resumes", v["state"] == "ACTIVE" and traffic("nexus-lab-client1", "curl", "-sf", "-m", "3", f"http://{SERVER}/"))

    print("== 9. RBAC & audit")
    if DEMO_PW:
        ta = login("analyst", DEMO_PW)
        s, _ = call("POST", f"/vnfs/{vid}/stop", token=ta)
        check("analyst cannot stop VNF (403)", s == 403)
        s, _ = call("GET", "/audit-logs?size=1", token=ta)
        check("analyst can read audit log", s == 200)
    _, au = call("GET", "/audit-logs?action=FIREWALL_RULE_CREATED&size=5", token=t)
    check("rule changes audited", au["totalElements"] >= 3)
    s, pdf = call("GET", "/reports/security.pdf?hours=1", token=t, raw=True)
    check("PDF security report", s == 200 and pdf[:5] == b"%PDF-", f"{len(pdf)} bytes")

    print("== 10. terminate & cleanup")
    call("POST", f"/vnfs/{vid}/terminate", token=t)
    v = wait_state(t, vid, {"TERMINATED", "FAILED"})
    check("terminate -> TERMINATED (container removed)", v["state"] == "TERMINATED" and
          subprocess.run(["docker", "inspect", f"nexus-vnf-{VNF_NAME}"], capture_output=True).returncode != 0)
    time.sleep(1)
    _, devs = call("GET", "/netconf/devices", token=t)
    check("auto-registered NETCONF device removed with the VNF", not any(d.get("vnfId") == vid for d in devs))
    if os.environ.get("E2E_KEEP") != "1":
        s, _ = call("DELETE", f"/vnfs/{vid}", token=t)
        check("delete VNF record", s == 204)


if __name__ == "__main__":
    try:
        main()
    finally:
        failed = [n for n, ok in RESULTS if not ok]
        print(f"\n{len(RESULTS) - len(failed)}/{len(RESULTS)} checks passed")
        for n in failed:
            print("  FAILED:", n)
        sys.exit(len(failed))
