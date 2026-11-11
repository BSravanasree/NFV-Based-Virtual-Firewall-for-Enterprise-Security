# Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `docker compose up` fails: `set DB_PASSWORD in .env` | required variable missing | copy `.env.example` to `.env` and fill every secret |
| Cannot log in, backend log: "No users exist and NEXUS_ADMIN_PASSWORD is not set" | no bootstrap password | set `NEXUS_ADMIN_PASSWORD`, restart backend |
| Backend exits: `NEXUS_CREDENTIAL_KEY must be 32 bytes` | key is not base64 of 32 bytes | `openssl rand -base64 32` |
| Backend exits with Flyway/validation error | schema drift or manual DB edits | restore DB or drop the volume in a lab: `docker compose down -v` |
| Login returns 423 | account locked after 5 failures | wait 15 min or ADMIN → Users → Unlock |
| Deploy ends in FAILED: "VNF image … is not present" | VNF image not built | `docker compose --profile build build fw-vnf-image` |
| Deploy FAILED: "lab network 'nexus-lan' does not exist" | lab profile not started | `docker compose --profile lab up -d` |
| Deploy FAILED: "Docker Engine: unreachable" | docker-proxy not running / backend outside compose | `docker compose ps docker-proxy`; run backend in compose |
| Deploy FAILED: "Firewall agent did not become healthy" | VNF crashed on start | `docker logs nexus-vnf-<name>`; check `NEXUS_AGENT_TOKEN` length ≥ 16 |
| Deploy FAILED: "Address already in use" | another container holds `10.10.0.254` / `10.20.0.254` | give the VNF different LAN/DMZ IPs or terminate the other VNF |
| Rules show "saved but NOT enforced" | VNF not ACTIVE or agent unreachable | check VNF health; *Apply now*; the reconciler retries every 30 s |
| Traffic not blocked/allowed as expected | client route missing / rule order | `docker exec nexus-lab-client1 ip route` must show `10.20.0.0/24 via 10.10.0.254`; check rule priorities and *View nftables* |
| No traffic events, counters fine | ulogd not running in the VNF | `docker exec nexus-vnf-<name> cat /var/log/ulog/ulogd.log`; kernel must support `nfnetlink_log` |
| NETCONF device `UNREACHABLE` | Netopeer2 not started | `docker exec nexus-vnf-<name> cat /var/log/netopeer2.log` |
| NETCONF "SSH host key … changed" | VNF re-deployed with a new key, or MITM | verify, then ADMIN → NETCONF → Reset host key |
| NETCONF `rpc-error access-denied` | NACM | the platform user must be in `nexus-admins` (set by `init-netconf.sh`) |
| ONOS page red "unreachable" | wrong `ONOS_URL`/credentials, ONOS still booting | `curl -u onos:<pw> $ONOS_URL/applications`; ONOS needs ~1–2 min to start |
| Everything SDN shows purple DEMO | `NEXUS_ONOS_MODE=demo` | set `real` + ONOS variables |
| OSM page "not configured" | expected without OSM | see [OSM_INTEGRATION.md](OSM_INTEGRATION.md) |
| Metrics show "NO DATA" | VNF just started (first sample is a baseline) or OSM-managed VNF | wait one poll interval (10 s); OSM metrics come from OSM MON |
| Frontend 502 on /api | backend not healthy yet | `docker compose logs backend` |
| Policy applied but no traffic reaches the VNF (all counters 0) | lab LAN/DMZ networks were created as `internal` | they must not be internal (see `docker-compose.yml`); `docker compose --profile lab down && … up -d` |
| Agent answers 411 "Content-Length required" | a client sent a chunked body | the backend buffers requests; custom clients must send `Content-Length` |
| Code changes not visible after `docker compose build` | build ran on a different buildx builder and the image was not loaded | `docker build -t nexus/backend:1.0.0 backend` then `docker compose up -d --force-recreate backend`; check `docker images` timestamps |
| Windows: scripts fail with `\r` errors | CRLF checkout | repository enforces LF via `.gitattributes`; re-clone or `git add --renormalize .` |

Useful commands:

```bash
docker compose logs -f backend
docker exec nexus-vnf-<name> nft list table inet nexus_fw
docker exec nexus-vnf-<name> tail -f /var/log/ulog/nexus-events.json
docker exec nexus-lab-client1 curl -m 3 http://10.20.0.10/
docker network inspect nexus-lan
```
