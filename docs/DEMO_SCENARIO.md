# End-to-End Demonstration Scenario

Repeatable 15-minute walkthrough on the Docker NFV lab. Every step has an observable, verifiable result.
A scripted version of the traffic checks is in `lab/verify-traffic.sh` (bash) and `lab/verify-traffic.ps1`
(PowerShell).

```
 client1 10.10.0.11 ─┐                                         
                     ├── nexus-lan 10.10.0.0/24 ── [fw VNF .254 | .254] ── nexus-dmz 10.20.0.0/24 ── server 10.20.0.10
 client2 10.10.0.12 ─┘                         (nftables, agent, NETCONF)            (HTTP 80/8080, TCP 22/3306, UDP 5353)
                                         mgmt 10.30.0.0/24 ── backend 10.30.0.10
```

## 0. Start

```bash
docker compose --profile build build fw-vnf-image
docker compose --profile lab up -d --build
```

Open <http://localhost:3000>, sign in as `admin`. Dashboard posture: **UNPROTECTED – No active firewall VNF**.

Before the firewall exists, the lab is isolated (internal networks, route via the not-yet-existing VNF):

```bash
docker exec nexus-lab-client1 curl -s -m 3 http://10.20.0.10/ || echo "no path (expected)"
```

## 1. Create and deploy the firewall VNF

1. *VNF Management → Create VNF*: name `fw-edge-01`, LAN `10.10.0.254`, DMZ `10.20.0.254`, 0.5 vCPU, 256 MiB,
   default policy **DROP**.
2. On the VNF page click **Deploy**. Watch the lifecycle: `CREATED → INSTANTIATING → ACTIVE` (≈5–10 s).
   Lifecycle history shows `DEPLOY STARTED` then `SUCCEEDED: Container … running; agent healthy`.
3. *Network Topology → NFV lab* shows client1/client2 on LAN, server on DMZ, `fw-edge-01` attached to LAN, DMZ
   and management segments.

Default-deny is now enforced in the forward path:

```bash
docker exec nexus-lab-client1 curl -s -m 3 http://10.20.0.10/ || echo BLOCKED     # -> BLOCKED
```

## 2. Allow web traffic for client1 only

*Firewall Policy → Add rule*: `allow-web-client1`, priority 10, ALLOW, TCP, source `10.10.0.11`,
destination `10.20.0.10`, port `80`. The toast reads **"enforced on VNF – Applied revision …"**.

```bash
docker exec nexus-lab-client1 curl -s -m 3 http://10.20.0.10/       # -> "NEXUS lab server … port 80 – client 10.10.0.11"
docker exec nexus-lab-client2 curl -s -m 3 http://10.20.0.10/ || echo BLOCKED     # -> BLOCKED
```

Click **View nftables** to show the exact ruleset in the VNF (`rule:<id>` counter now > 0).

## 3. Allow ICMP from the LAN, explicitly deny SSH

* Rule `allow-ping-lan`: priority 20, ALLOW, ICMP, source `10.10.0.0/24`.
* Rule `deny-ssh`: priority 5, DENY, TCP, destination port `22` (drag order with ↑/↓ to show re-ordering).

```bash
docker exec nexus-lab-client2 ping -c 2 -W 1 10.20.0.10                 # replies
docker exec nexus-lab-client1 nc -z -w 2 10.20.0.10 22 || echo BLOCKED  # -> BLOCKED by rule deny-ssh
```

Disable `allow-ping-lan` with the toggle → ping fails; re-enable → works (each change is a new revision).

## 4. Monitoring

*Traffic Monitoring*: allowed vs blocked packet chart, protocol mix, top blocked sources, connection log
filtered by `BLOCKED` / `10.10.0.12`, **Export CSV**. All figures carry the blue `LAB` badge.

## 5. Attack simulation and detection

```bash
docker exec nexus-lab-client2 nmap -Pn -T4 --max-retries 0 -p 1-200 10.20.0.10      # port scan
for i in $(seq 1 8); do docker exec nexus-lab-client2 nc -z -w 1 10.20.0.10 3306; done # DB brute force
```

*Security Alerts → Run detection now*: **PORT_SCAN (HIGH/CRITICAL)**, **REPEATED_BLOCKED**,
**UNAUTHORIZED_ACCESS** and **UNEXPECTED_COMMUNICATION** for `10.10.0.12`. Open one, acknowledge, add a note,
resolve. The dashboard posture changes to **AT RISK / CRITICAL** while alerts are active.

## 6. NETCONF

*NETCONF*: device `fw-edge-01-netconf` was registered automatically. **Test connection** (capabilities, pinned
host key) → **Get running config** (the YANG view of the enforced policy) → **Check sync** = `IN_SYNC` →
**Push policy** (lock/edit/validate/commit) → history v1. Change a rule in the Firewall page, **Check sync**
shows `IN_SYNC` again (REST pushes are mirrored into the datastore). **Rollback** to v1 → the VNF enforces the
old policy (agent `rulesSource=netconf`); the reconciler restores the platform's desired policy within 30 s,
which demonstrates drift correction.

## 7. Lifecycle operations

*VNF details*: **Scale** (vertical 1 vCPU / 384 MiB) → **Restart** (policy restored automatically, traffic
checks still pass) → **Stop** (all LAN↔DMZ traffic stops – the firewall is the only path) → **Start** →
**Terminate** → **Delete**.

## 8. SDN (ONOS)

*ONOS Controller / OpenFlow / Topology (SDN)*: in DEMO mode a purple banner states the data is a fixture. Create
a network policy `DROP TCP 10.0.1.11 → :22 on of:0000000000000001` → status INSTALLED (DEMO), flow visible in the
flow table, delete it. With a real controller (`lab/sdn/`), the same actions change OVS flow tables.

## 9. Governance

*Audit Logs*: filter by user `admin` / action `FIREWALL_RULE_CREATED`, export CSV, generate the **PDF security
report**. Sign in as `analyst`: VNF actions are hidden and the API returns 403; alert triage is allowed.
