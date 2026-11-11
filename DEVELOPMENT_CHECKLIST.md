# NEXUS NFV – Development Checklist

Legend: `[x]` done & verified · `[~]` implemented, not verified against live infrastructure · `[ ]` pending

## Backend (Spring Boot)
- [x] Flyway V1/V2 schema on MySQL 8.4 (Hibernate validate) and H2 for tests
- [x] Auth: JWT + revocable sessions, BCrypt, lockout, RBAC (3 roles), no default users
- [x] Users, audit trail, CSV export, PDF report
- [x] VNF lifecycle state machine, async engine, monitor (Docker lab orchestrator verified live)
- [~] OSM NBI orchestrator (no OSM available)
- [x] Firewall rules, validation, agent sync, reconciler
- [x] Traffic collection (counters + NFLOG), analytics, CSV
- [x] Rule-based detection (4 detectors verified live) + alert triage
- [x] ONOS DEMO mode (labelled) · [~] ONOS REAL mode (no controller available)
- [x] NETCONF client (MINA SSHD): test, get-config, validate, push, sync, rollback – verified live against Netopeer2
- [x] Dashboard, settings, integrations health, OpenAPI

## Firewall VNF
- [x] Ubuntu 24.04 + nftables + hardened agent
- [x] NFLOG → ulogd2 → events
- [x] Netopeer2/sysrepo (pinned source build) + `nexus-firewall` YANG + datastore bridge
- [x] Docker lab (2 clients, firewall, server): allowed/blocked traffic verified
- [~] Mininet/OVS/ONOS lab script (needs Ubuntu host)

## Frontend
- [x] 16 pages, RBAC-aware navigation, toasts, dialogs, loading/empty/error states, provenance badges
- [x] 19 Vitest/RTL tests, typecheck, production build

## Delivery
- [x] Docker Compose (app + lab + optional ONOS), socket proxy, health checks
- [x] Backend 51 tests · Frontend 19 tests · E2E 52 checks
- [x] OSM SOL006 packages + onboarding script
- [x] Documentation set + Postman collection

## Possible next steps
- [ ] Run OSM adapter against a real OSM + VIM
- [ ] Run `lab/sdn` on an Ubuntu VM with ONOS (`NEXUS_ONOS_MODE=real`)
- [ ] TLS for the agent (per-VNF certificates / mTLS) and TLS termination in front of nginx
