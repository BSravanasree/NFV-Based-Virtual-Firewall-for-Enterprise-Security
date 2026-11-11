# Firewall Implementation

## The VNF image (`vnf-firewall/`)

| Component | Role |
|---|---|
| Ubuntu 24.04 | base OS of the VNF |
| nftables 1.0.9 | packet filter; the agent owns one table: `inet nexus_fw` |
| ulogd2 + JSON plugin | receives NFLOG group 1 and writes one JSON line per logged packet header |
| `agent/nexus_agent.py` | REST agent (Python standard library only) – the only writer of the ruleset |
| Netopeer2 + sysrepo + libyang + libnetconf2 | NETCONF server; built from pinned upstream sources (not packaged for Ubuntu 24.04) |
| `netconf/yang/nexus-firewall@2026-11-01.yang` | YANG model of the policy |
| tini | PID 1 / signal handling |

Two targets: `fw-base` (packet filter only) and `fw-vnf` (default, adds NETCONF).

## Generated nftables ruleset

For a policy with default `drop`, an allow rule (id 11) and a deny rule (id 12) the agent renders and
applies (output captured from a running VNF):

```
table inet nexus_fw {
  counter proto_tcp { } … counter dir_inbound { } counter dir_outbound { }
  chain forward {
    type filter hook forward priority filter; policy drop;
    meta l4proto tcp counter name "proto_tcp"                      # protocol accounting
    meta l4proto udp counter name "proto_udp"
    meta l4proto icmp counter name "proto_icmp"
    iifname "eth1" counter name "dir_outbound"                     # LAN -> DMZ bytes
    iifname "eth2" counter name "dir_inbound"                      # DMZ -> LAN bytes
    ct state invalid counter drop comment "sys:invalid"
    ct state established,related counter accept comment "sys:established"
    ip saddr 10.10.0.11 ip daddr 10.20.0.10 tcp dport 80 limit rate 50/second burst 100 packets log prefix "NX|A|11|" group 1 comment "log:11"
    ip saddr 10.10.0.11 ip daddr 10.20.0.10 tcp dport 80 counter accept comment "rule:11"
    tcp dport 22 limit rate 50/second burst 100 packets log prefix "NX|D|12|" group 1 comment "log:12"
    tcp dport 22 counter drop comment "rule:12"
    counter comment "sys:default"
    limit rate 200/second burst 400 packets log prefix "NX|D|0|" group 1 comment "log:default"
  }
  chain input {
    type filter hook input priority filter; policy accept;
    iifname != "eth0" tcp dport { 830, 9443 } counter drop comment "sys:protect-mgmt"
  }
}
```

Design decisions:

* **Stateful:** established/related traffic is accepted first, so rules describe *new* connections and reply
  traffic needs no rule. Invalid conntrack state is dropped.
* **Default deny:** the forward chain policy is `drop` unless the operator explicitly chooses `accept`.
* **First match wins:** rules are emitted in ascending priority.
* **Exact counters + rate-limited logging:** each rule is split into a rate-limited `log` rule and a verdict rule
  with its own counter. Logging can never change the verdict or the counter, and a flood cannot overwhelm
  the logger (excess packets are still counted and filtered).
* **Atomic replace:** the script starts with `table …; delete table …; table … {…}` and is applied in one
  `nft -f` transaction after a successful `nft -c` dry run – there is never a half-applied policy.
* **Management protection:** agent (9443) and NETCONF (830) are only reachable via the management interface.
* **Interface roles** are discovered from the configured LAN/DMZ CIDRs (`ip -j addr`), not from interface order.
* **Fail closed:** on start the agent restores the last applied policy from `/var/lib/nexus-agent/ruleset.json`;
  with no saved policy it applies default-deny with no rules.

## Agent API (management network only)

All endpoints require `Authorization: Bearer <NEXUS_AGENT_TOKEN>` (constant-time comparison).

| Method & path | Description |
|---|---|
| `GET /health` | status, nftables present, applied revision, source (`rest`/`netconf`/`restore`), interfaces, boot id |
| `PUT /v1/ruleset` | `{revision, defaultAction, rules[]}`; 400 on invalid input, 409 on stale revision, 500 if nft rejects |
| `GET /v1/ruleset` | current policy + `nft list table inet nexus_fw` output |
| `GET /v1/counters` | per-rule, established, default-drop, invalid, protocol and direction counters |
| `GET /v1/events?since=N&limit=M` | NFLOG events with sequence numbers and `bootId` (restart detection) |

### Input validation (defence in depth – the backend validates the same rules first)

* `id` positive & unique; `priority` 1–65535 & unique; `action` ∈ {accept, drop};
  `protocol` ∈ {any, tcp, udp, icmp}
* CIDRs parsed with `ipaddress.ip_network(strict=True)`, IPv4 only – arbitrary text cannot reach the script
* ports: `^\d{1,5}(-\d{1,5})?$`, 1–65535, ascending, only with tcp/udp
* at most 2000 rules, body ≤ 1 MiB
* only fixed binaries (`/usr/sbin/nft`, `ip`, `sysrepocfg`) are executed, with argument lists – never a shell

Verified behaviour (smoke test output in [TESTING_GUIDE.md](TESTING_GUIDE.md)): unauthenticated → 401,
`"srcCidr":"10.0.0.0/8; flush ruleset"` → 400, ports with ICMP → 400, stale revision → 409.

## Events

ulogd2 decodes headers (`BASE`, `IP2STR`) and writes JSON. The agent tails the file, parses the
`NX|<A|D>|<ruleId>|` prefix, and keeps a bounded buffer (20 000 events) with sequence numbers. The file is
truncated safely when it exceeds 50 MiB (ulogd writes with `O_APPEND`).

## Backend side

* `FirewallRuleService` – CRUD, normalisation (`Ipv4.normalizeCidr` rejects host bits outside the prefix),
  priority/name uniqueness, two-phase renumbering for reorder, revision bump, audit.
* `FirewallSyncService` – desired-state push with per-VNF lock, confirms `appliedRevision`, records
  `IN_SYNC / PENDING / FAILED / DRIFT`, reconciler every 30 s, initial push after activation.
* `TrafficCollector` – counter deltas (handles table re-creation), cumulative rule hit counters, events.
