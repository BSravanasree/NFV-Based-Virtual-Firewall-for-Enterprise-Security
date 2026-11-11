#!/usr/bin/env python3
"""
NEXUS firewall VNF agent.

Runs inside the Ubuntu firewall VNF and is the only component allowed to change the packet filter.
  * Accepts a *structured* ruleset (JSON) from the control plane, validates every field, renders an
    nftables script and applies it atomically (`nft -c -f` dry-run, then `nft -f`).
  * Never executes a shell and never interpolates free text into commands: only fixed binaries
    (nft, ip, sysrepocfg) with argument lists are invoked.
  * Exposes exact nftables counters and NFLOG packet-header events (collected by ulogd2).
  * Optional NETCONF bridge: mirrors the sysrepo `nexus-firewall` running datastore <-> nftables.

Standard library only (Python 3.12, Ubuntu 24.04).
"""
from __future__ import annotations

import hmac
import ipaddress
import json
import logging
import os
import re
import socket
import ssl
import subprocess
import tempfile
import threading
import time
import uuid
from collections import deque
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

AGENT_VERSION = "1.0.0"
TABLE = "nexus_fw"
NFT = "/usr/sbin/nft"
IP = "/usr/sbin/ip" if os.path.exists("/usr/sbin/ip") else "/sbin/ip"
SYSREPOCFG = "/usr/local/bin/sysrepocfg"
YANG_MODULE = "nexus-firewall"

PORT = int(os.environ.get("AGENT_PORT", "9443"))
TOKEN = os.environ.get("NEXUS_AGENT_TOKEN", "")
LAN_CIDR = os.environ.get("NEXUS_LAN_CIDR", "")
WAN_CIDR = os.environ.get("NEXUS_WAN_CIDR", "")
STATE_DIR = os.environ.get("AGENT_STATE_DIR", "/var/lib/nexus-agent")
EVENT_FILE = os.environ.get("AGENT_EVENT_FILE", "/var/log/ulog/nexus-events.json")
NETCONF_ENABLED = os.environ.get("NETCONF_ENABLED", "false").lower() == "true"
TLS_CERT = os.environ.get("AGENT_TLS_CERT", "/etc/nexus-agent/tls.crt")
TLS_KEY = os.environ.get("AGENT_TLS_KEY", "/etc/nexus-agent/tls.key")

MAX_RULES = 2000
MAX_BODY = 1024 * 1024
EVENT_BUFFER = 20000
EVENT_FILE_MAX = 50 * 1024 * 1024
LOG_RATE = "50/second burst 100 packets"
DEFAULT_LOG_RATE = "200/second burst 400 packets"

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s nexus-agent: %(message)s")
log = logging.getLogger("nexus-agent")

BOOT_ID = uuid.uuid4().hex
STARTED = time.time()
_apply_lock = threading.Lock()


class ValidationError(ValueError):
    pass


# --------------------------------------------------------------------------------------------- helpers

def run(args: list[str], stdin: str | None = None, timeout: int = 15) -> subprocess.CompletedProcess:
    """Runs a fixed binary with an argument list (no shell)."""
    return subprocess.run(args, input=stdin, capture_output=True, text=True, timeout=timeout, check=False)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def detect_interfaces() -> dict:
    """Maps lan/wan/mgmt roles to interface names using the configured subnets."""
    roles = {}
    try:
        res = run([IP, "-j", "-4", "addr", "show"])
        data = json.loads(res.stdout or "[]")
    except Exception as exc:  # pragma: no cover - defensive
        log.warning("interface detection failed: %s", exc)
        return roles
    lan = ipaddress.ip_network(LAN_CIDR) if LAN_CIDR else None
    wan = ipaddress.ip_network(WAN_CIDR) if WAN_CIDR else None
    for itf in data:
        name = itf.get("ifname")
        if not name or name == "lo":
            continue
        for a in itf.get("addr_info", []):
            try:
                addr = ipaddress.ip_address(a.get("local"))
            except ValueError:
                continue
            if lan and addr in lan:
                roles["lan"] = name
            elif wan and addr in wan:
                roles["wan"] = name
            else:
                roles.setdefault("mgmt", name)
    return roles


IFNAME_RE = re.compile(r"^[A-Za-z0-9_.@-]{1,15}$")
PORT_RE = re.compile(r"^\d{1,5}(-\d{1,5})?$")


def _port(value, field):
    if value is None or value == "":
        return None
    s = str(value)
    if not PORT_RE.match(s):
        raise ValidationError(f"{field}: invalid port specification")
    parts = [int(p) for p in s.split("-")]
    if any(p < 1 or p > 65535 for p in parts) or (len(parts) == 2 and parts[0] > parts[1]):
        raise ValidationError(f"{field}: ports must be 1-65535 and ascending")
    return s


def _cidr(value, field):
    if value is None or value == "":
        return None
    try:
        net = ipaddress.ip_network(str(value), strict=True)
    except ValueError as exc:
        raise ValidationError(f"{field}: {exc}") from None
    if net.version != 4:
        raise ValidationError(f"{field}: only IPv4 is supported")
    return str(net)


def validate_ruleset(doc) -> dict:
    """Strict validation of the control-plane ruleset. Returns a normalised copy."""
    if not isinstance(doc, dict):
        raise ValidationError("ruleset must be an object")
    revision = doc.get("revision")
    if not isinstance(revision, int) or revision < 0:
        raise ValidationError("revision must be a non-negative integer")
    default = doc.get("defaultAction", "drop")
    if default not in ("accept", "drop"):
        raise ValidationError("defaultAction must be accept or drop")
    rules = doc.get("rules", [])
    if not isinstance(rules, list) or len(rules) > MAX_RULES:
        raise ValidationError(f"rules must be a list of at most {MAX_RULES} entries")
    out, ids, prios = [], set(), set()
    for i, r in enumerate(rules):
        if not isinstance(r, dict):
            raise ValidationError(f"rules[{i}] must be an object")
        rid, prio = r.get("id"), r.get("priority")
        if not isinstance(rid, int) or rid <= 0 or rid in ids:
            raise ValidationError(f"rules[{i}].id must be a unique positive integer")
        if not isinstance(prio, int) or not 1 <= prio <= 65535 or prio in prios:
            raise ValidationError(f"rules[{i}].priority must be unique and within 1-65535")
        action = r.get("action")
        if action not in ("accept", "drop"):
            raise ValidationError(f"rules[{i}].action must be accept or drop")
        proto = r.get("protocol", "any")
        if proto not in ("any", "tcp", "udp", "icmp"):
            raise ValidationError(f"rules[{i}].protocol must be any, tcp, udp or icmp")
        sport, dport = _port(r.get("srcPort"), f"rules[{i}].srcPort"), _port(r.get("dstPort"), f"rules[{i}].dstPort")
        if (sport or dport) and proto not in ("tcp", "udp"):
            raise ValidationError(f"rules[{i}]: ports require protocol tcp or udp")
        logflag = r.get("log", True)
        if not isinstance(logflag, bool):
            raise ValidationError(f"rules[{i}].log must be boolean")
        ids.add(rid)
        prios.add(prio)
        out.append({"id": rid, "priority": prio, "action": action, "protocol": proto,
                    "srcCidr": _cidr(r.get("srcCidr"), f"rules[{i}].srcCidr"),
                    "dstCidr": _cidr(r.get("dstCidr"), f"rules[{i}].dstCidr"),
                    "srcPort": sport, "dstPort": dport, "log": logflag})
    out.sort(key=lambda x: x["priority"])
    return {"revision": revision, "defaultAction": default, "rules": out}


def _port_expr(spec: str) -> str:
    return spec  # "80" or "1000-2000" – already validated, valid nft syntax


def render(ruleset: dict, ifaces: dict) -> str:
    """Renders a validated ruleset into an atomic nftables script (table replaced in one transaction)."""
    lines = [f"table inet {TABLE}", f"delete table inet {TABLE}", f"table inet {TABLE} {{"]
    for c in ("proto_tcp", "proto_udp", "proto_icmp", "dir_inbound", "dir_outbound"):
        lines.append(f"  counter {c} {{ }}")
    policy = "accept" if ruleset["defaultAction"] == "accept" else "drop"
    lines += ["  chain forward {", f"    type filter hook forward priority filter; policy {policy};",
              '    meta l4proto tcp counter name "proto_tcp"',
              '    meta l4proto udp counter name "proto_udp"',
              '    meta l4proto icmp counter name "proto_icmp"']
    lan, wan = ifaces.get("lan"), ifaces.get("wan")
    if lan and IFNAME_RE.match(lan):
        lines.append(f'    iifname "{lan}" counter name "dir_outbound"')
    if wan and IFNAME_RE.match(wan):
        lines.append(f'    iifname "{wan}" counter name "dir_inbound"')
    lines.append('    ct state invalid counter drop comment "sys:invalid"')
    lines.append('    ct state established,related counter accept comment "sys:established"')

    for r in ruleset["rules"]:
        match = []
        if r["srcCidr"]:
            match.append(f"ip saddr {r['srcCidr']}")
        if r["dstCidr"]:
            match.append(f"ip daddr {r['dstCidr']}")
        if r["protocol"] in ("tcp", "udp"):
            match.append(f"meta l4proto {r['protocol']}")
            if r["srcPort"]:
                match.append(f"{r['protocol']} sport {_port_expr(r['srcPort'])}")
            if r["dstPort"]:
                match.append(f"{r['protocol']} dport {_port_expr(r['dstPort'])}")
        elif r["protocol"] == "icmp":
            match.append("meta l4proto icmp")
        m = " ".join(match)
        tag = "A" if r["action"] == "accept" else "D"
        if r["log"]:
            # separate, rate-limited log rule keeps the verdict rule's counter exact
            lines.append(f'    {m} limit rate {LOG_RATE} log group 1 prefix "NX|{tag}|{r["id"]}|" comment "log:{r["id"]}"'.replace("     ", "    "))
        lines.append(f'    {m} counter {r["action"]} comment "rule:{r["id"]}"'.replace("     ", "    "))

    lines.append('    counter comment "sys:default"')
    if policy == "drop":
        lines.append(f'    limit rate {DEFAULT_LOG_RATE} log group 1 prefix "NX|D|0|" comment "log:default"')
    lines.append("  }")
    # Management plane protection: agent/NETCONF ports only reachable from the management interface.
    mgmt = ifaces.get("mgmt")
    lines += ["  chain input {", "    type filter hook input priority filter; policy accept;"]
    if mgmt and IFNAME_RE.match(mgmt):
        lines.append(f'    iifname != "{mgmt}" tcp dport {{ {PORT}, 830 }} counter drop comment "sys:protect-mgmt"')
    lines += ["  }", "}"]
    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------------------------- state

class FirewallState:
    def __init__(self):
        os.makedirs(STATE_DIR, exist_ok=True)
        self.path = os.path.join(STATE_DIR, "ruleset.json")
        self.ruleset = {"revision": 0, "defaultAction": "drop", "rules": []}
        self.source = "none"
        self.applied_at = None
        self.ifaces = {}

    def load(self):
        if os.path.exists(self.path):
            with open(self.path, encoding="utf-8") as f:
                saved = json.load(f)
            self.ruleset = validate_ruleset(saved["ruleset"])
            self.source = "restore"
        return self

    def persist(self):
        tmp = self.path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump({"ruleset": self.ruleset, "source": self.source, "appliedAt": self.applied_at}, f)
        os.replace(tmp, self.path)

    def apply(self, ruleset: dict, source: str, sync_netconf: bool = True) -> dict:
        with _apply_lock:
            self.ifaces = detect_interfaces()
            script = render(ruleset, self.ifaces)
            with tempfile.NamedTemporaryFile("w", suffix=".nft", delete=False, dir=STATE_DIR) as f:
                f.write(script)
                path = f.name
            try:
                check = run([NFT, "-c", "-f", path])
                if check.returncode != 0:
                    raise RuntimeError("nft validation failed: " + check.stderr.strip()[:500])
                res = run([NFT, "-f", path])
                if res.returncode != 0:
                    raise RuntimeError("nft apply failed: " + res.stderr.strip()[:500])
            finally:
                os.unlink(path)
            self.ruleset = ruleset
            self.source = source
            self.applied_at = utc_now()
            self.persist()
            log.info("applied revision %s (%d rules, default %s) from %s", ruleset["revision"],
                     len(ruleset["rules"]), ruleset["defaultAction"], source)
        if sync_netconf and NETCONF_ENABLED:
            netconf_bridge.export(ruleset)
        return {"appliedRevision": ruleset["revision"], "ruleCount": len(ruleset["rules"]), "appliedAt": self.applied_at}


STATE = FirewallState()


def read_counters() -> dict:
    res = run([NFT, "-j", "list", "table", "inet", TABLE])
    if res.returncode != 0:
        raise RuntimeError("nft list failed: " + res.stderr.strip()[:300])
    items = json.loads(res.stdout).get("nftables", [])
    rules, named = {}, {}
    established = default_drop = invalid = {"packets": 0, "bytes": 0}
    for it in items:
        if "counter" in it and "name" in it["counter"]:
            c = it["counter"]
            named[c["name"]] = {"packets": c.get("packets", 0), "bytes": c.get("bytes", 0)}
        rule = it.get("rule")
        if not rule or rule.get("chain") != "forward":
            continue
        comment = rule.get("comment", "")
        counter = next((e["counter"] for e in rule.get("expr", []) if isinstance(e, dict) and "counter" in e
                        and isinstance(e["counter"], dict) and "packets" in e["counter"]), None)
        if counter is None:
            continue
        val = {"packets": counter["packets"], "bytes": counter["bytes"]}
        if comment.startswith("rule:"):
            rules[comment[5:]] = val
        elif comment == "sys:established":
            established = val
        elif comment == "sys:invalid":
            invalid = val
        elif comment == "sys:default" and STATE.ruleset["defaultAction"] == "drop":
            default_drop = val
    zero = {"packets": 0, "bytes": 0}
    return {
        "timestamp": utc_now(),
        "appliedRevision": STATE.ruleset["revision"],
        "rules": rules,
        "established": established,
        "defaultDrop": default_drop,
        "invalid": invalid,
        "protocols": {"tcp": named.get("proto_tcp", zero), "udp": named.get("proto_udp", zero),
                      "icmp": named.get("proto_icmp", zero)},
        "direction": {"inbound": named.get("dir_inbound", zero), "outbound": named.get("dir_outbound", zero)},
    }


# --------------------------------------------------------------------------------------------- events

PROTO = {1: "ICMP", 6: "TCP", 17: "UDP"}


class EventTail(threading.Thread):
    """Follows the ulogd2 JSON file and keeps a bounded, sequence-numbered buffer of packet events."""

    def __init__(self):
        super().__init__(daemon=True, name="event-tail")
        self.buffer = deque(maxlen=EVENT_BUFFER)
        self.seq = 0
        self.lock = threading.Lock()
        self.offset = 0

    def available(self) -> bool:
        return os.path.exists(EVENT_FILE)

    def run(self):
        while True:
            try:
                self._poll()
            except Exception as exc:  # keep tailing whatever happens
                log.debug("event tail error: %s", exc)
            time.sleep(1)

    def _poll(self):
        if not os.path.exists(EVENT_FILE):
            return
        size = os.path.getsize(EVENT_FILE)
        if size < self.offset:
            self.offset = 0  # truncated
        if size > EVENT_FILE_MAX and self.offset >= size:
            os.truncate(EVENT_FILE, 0)  # ulogd appends with O_APPEND, truncation is safe
            self.offset = 0
            return
        with open(EVENT_FILE, encoding="utf-8", errors="replace") as f:
            f.seek(self.offset)
            for line in f:
                if not line.endswith("\n"):
                    break  # partial line – re-read next time
                self.offset += len(line.encode("utf-8"))
                ev = self._parse(line)
                if ev:
                    with self.lock:
                        self.seq += 1
                        ev["seq"] = self.seq
                        self.buffer.append(ev)

    @staticmethod
    def _parse(line: str):
        try:
            j = json.loads(line)
        except ValueError:
            return None
        prefix = j.get("oob.prefix", "")
        parts = prefix.split("|")
        if len(parts) < 3 or parts[0] != "NX":
            return None
        rid = int(parts[2]) if parts[2].isdigit() and parts[2] != "0" else None
        sec, usec = j.get("oob.time.sec"), j.get("oob.time.usec", 0)
        ts = (datetime.fromtimestamp(sec + usec / 1e6, tz=timezone.utc).isoformat().replace("+00:00", "Z")
              if sec else utc_now())
        pnum = j.get("ip.protocol")
        return {
            "ts": ts,
            "action": "ALLOW" if parts[1] == "A" else "DROP",
            "ruleId": rid,
            "protocol": PROTO.get(pnum, str(pnum) if pnum is not None else "OTHER"),
            "srcIp": j.get("src_ip"),
            "dstIp": j.get("dest_ip"),
            "srcPort": j.get("src_port", j.get("tcp.sport", j.get("udp.sport"))),
            "dstPort": j.get("dest_port", j.get("tcp.dport", j.get("udp.dport"))),
            "inIface": j.get("oob.in"),
            "length": j.get("ip.totlen"),
        }

    def since(self, seq: int, limit: int):
        with self.lock:
            items = [e for e in self.buffer if e["seq"] > seq][:limit]
            next_seq = items[-1]["seq"] if items else max(seq, 0) if seq <= self.seq else self.seq
        return items, next_seq


EVENTS = EventTail()


# --------------------------------------------------------------------------------------------- NETCONF bridge

class NetconfBridge(threading.Thread):
    """Keeps the sysrepo running datastore (nexus-firewall YANG) and nftables consistent in both directions."""

    def __init__(self):
        super().__init__(daemon=True, name="netconf-bridge")
        self.last_seen = None

    @staticmethod
    def to_yang(ruleset: dict) -> dict:
        rules = []
        for r in ruleset["rules"]:
            y = {"id": r["id"], "priority": r["priority"], "action": r["action"], "protocol": r["protocol"],
                 "enabled": True, "log": r["log"]}
            for k, yk in (("srcCidr", "source-prefix"), ("dstCidr", "destination-prefix"),
                          ("srcPort", "source-port"), ("dstPort", "destination-port")):
                if r[k]:
                    y[yk] = r[k]
            rules.append(y)
        return {f"{YANG_MODULE}:firewall": {"default-action": ruleset["defaultAction"],
                                             "revision": str(ruleset["revision"]), "rule": rules}}

    @staticmethod
    def from_yang(doc: dict) -> dict:
        fw = doc.get(f"{YANG_MODULE}:firewall", {})
        rules = []
        for r in fw.get("rule", []):
            if not r.get("enabled", True):
                continue
            rules.append({"id": int(r["id"]), "priority": int(r["priority"]), "action": r["action"],
                          "protocol": r.get("protocol", "any"), "srcCidr": r.get("source-prefix"),
                          "dstCidr": r.get("destination-prefix"), "srcPort": r.get("source-port"),
                          "dstPort": r.get("destination-port"), "log": bool(r.get("log", True))})
        return validate_ruleset({"revision": int(fw.get("revision", 0)),
                                 "defaultAction": fw.get("default-action", "drop"), "rules": rules})

    def export(self, ruleset: dict):
        data = json.dumps(self.to_yang(ruleset))
        res = run([SYSREPOCFG, "--import", "-d", "running", "-m", YANG_MODULE, "-f", "json"], stdin=data)
        if res.returncode != 0:
            log.warning("sysrepo export failed: %s", res.stderr.strip()[:300])
        else:
            self.last_seen = canonical(ruleset)

    def run(self):
        while True:
            time.sleep(3)
            try:
                res = run([SYSREPOCFG, "-X", "-d", "running", "-m", YANG_MODULE, "-f", "json"])
                if res.returncode != 0 or not res.stdout.strip():
                    continue
                desired = self.from_yang(json.loads(res.stdout))
                c = canonical(desired)
                if c != self.last_seen and c != canonical(STATE.ruleset):
                    log.info("NETCONF datastore changed (revision %s); applying to nftables", desired["revision"])
                    STATE.apply(desired, "netconf", sync_netconf=False)
                self.last_seen = c
            except Exception as exc:
                log.warning("NETCONF bridge cycle failed: %s", exc)


def canonical(rs: dict) -> str:
    return json.dumps({"d": rs["defaultAction"], "r": rs["rules"]}, sort_keys=True)


netconf_bridge = NetconfBridge()


# --------------------------------------------------------------------------------------------- HTTP API

class Handler(BaseHTTPRequestHandler):
    server_version = "nexus-agent/" + AGENT_VERSION
    sys_version = ""

    def log_message(self, fmt, *args):  # route to logging, never log headers (token)
        log.info("%s %s", self.address_string(), fmt % args)

    def _send(self, code: int, body: dict):
        data = json.dumps(body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)

    def _authorized(self) -> bool:
        header = self.headers.get("Authorization", "")
        if not TOKEN or not header.startswith("Bearer "):
            return False
        return hmac.compare_digest(header[7:].encode(), TOKEN.encode())

    def _guard(self) -> bool:
        if not self._authorized():
            self._send(401, {"error": "unauthorized"})
            return False
        return True

    def do_GET(self):
        url = urlparse(self.path)
        if not self._guard():
            return
        try:
            if url.path == "/health":
                nft_ok = run([NFT, "list", "table", "inet", TABLE]).returncode == 0
                self._send(200, {
                    "status": "ok" if nft_ok else "degraded", "hostname": socket.gethostname(), "bootId": BOOT_ID,
                    "appliedRevision": STATE.ruleset["revision"], "rulesSource": STATE.source,
                    "nftables": nft_ok, "netconf": NETCONF_ENABLED, "eventLogging": EVENTS.available(),
                    "interfaces": STATE.ifaces or detect_interfaces(), "uptimeSeconds": int(time.time() - STARTED),
                    "agentVersion": AGENT_VERSION})
            elif url.path == "/v1/ruleset":
                listing = run([NFT, "list", "table", "inet", TABLE])
                self._send(200, {"revision": STATE.ruleset["revision"], "defaultAction": STATE.ruleset["defaultAction"],
                                 "rules": STATE.ruleset["rules"], "source": STATE.source,
                                 "rendered": listing.stdout if listing.returncode == 0 else listing.stderr})
            elif url.path == "/v1/counters":
                self._send(200, read_counters())
            elif url.path == "/v1/events":
                q = parse_qs(url.query)
                since = max(0, int(q.get("since", ["0"])[0]))
                limit = min(5000, max(1, int(q.get("limit", ["500"])[0])))
                items, nxt = EVENTS.since(since, limit)
                self._send(200, {"bootId": BOOT_ID, "nextSeq": nxt, "available": EVENTS.available(), "events": items})
            else:
                self._send(404, {"error": "not found"})
        except ValueError:
            self._send(400, {"error": "invalid query parameter"})
        except Exception as exc:
            log.exception("GET %s failed", url.path)
            self._send(500, {"error": str(exc)[:300]})

    def do_PUT(self):
        if not self._guard():
            return
        if urlparse(self.path).path != "/v1/ruleset":
            self._send(404, {"error": "not found"})
            return
        if "chunked" in self.headers.get("Transfer-Encoding", "").lower():
            self._send(411, {"error": "Content-Length required (chunked transfer encoding is not accepted)"})
            return
        length = int(self.headers.get("Content-Length", "0") or 0)
        if length <= 0 or length > MAX_BODY:
            self._send(413 if length > MAX_BODY else 400,
                       {"error": f"body must be 1..{MAX_BODY} bytes with a Content-Length header"})
            return
        try:
            ruleset = validate_ruleset(json.loads(self.rfile.read(length)))
        except (ValidationError, ValueError) as exc:
            self._send(400, {"error": f"invalid ruleset: {exc}"})
            return
        if ruleset["revision"] < STATE.ruleset["revision"] and STATE.source != "none":
            self._send(409, {"error": f"stale revision {ruleset['revision']} < applied {STATE.ruleset['revision']}"})
            return
        try:
            self._send(200, STATE.apply(ruleset, "rest"))
        except Exception as exc:
            log.error("apply failed: %s", exc)
            self._send(500, {"error": str(exc)[:500]})


def main():
    if not TOKEN or len(TOKEN) < 16:
        raise SystemExit("NEXUS_AGENT_TOKEN must be set (>= 16 characters)")
    STATE.load()
    try:
        # restore persisted policy, or fail closed with default-deny and no rules
        STATE.apply(STATE.ruleset, STATE.source if STATE.source != "none" else "none", sync_netconf=NETCONF_ENABLED)
    except Exception as exc:
        log.error("initial apply failed: %s", exc)
    EVENTS.start()
    if NETCONF_ENABLED:
        netconf_bridge.start()
    httpd = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    if os.path.exists(TLS_CERT) and os.path.exists(TLS_KEY):
        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.minimum_version = ssl.TLSVersion.TLSv1_2
        ctx.load_cert_chain(TLS_CERT, TLS_KEY)
        httpd.socket = ctx.wrap_socket(httpd.socket, server_side=True)
        log.info("TLS enabled")
    log.info("agent %s listening on :%d (interfaces %s, netconf=%s)", AGENT_VERSION, PORT,
             STATE.ifaces, NETCONF_ENABLED)
    httpd.serve_forever()


if __name__ == "__main__":
    main()
