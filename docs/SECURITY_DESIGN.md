# Security Design

## Authentication

* `POST /api/v1/auth/login` → HS256 JWT (issuer `nexus-nfv`, 60 min default, claims: uid, role, jti).
* Every token is recorded in `user_sessions`; each request checks signature, expiry **and** that the session
  is not revoked and the user is enabled → logout, admin session revocation, disabling a user and password
  changes take effect immediately.
* BCrypt (cost 12). Password policy: ≥ 10 chars, upper, lower, digit, symbol, must not contain the username.
* Lockout: 5 failed attempts → 15 min lock (`423 ACCOUNT_LOCKED`). Unknown users get the same response and
  the same BCrypt cost (dummy hash) to resist username enumeration.
* No default credentials: the first admin comes from `NEXUS_ADMIN_PASSWORD`; without it no account is created.
* The SPA keeps the token in memory + `sessionStorage` (cleared when the tab closes); no cookies → no CSRF
  surface (CSRF protection is disabled deliberately for the stateless bearer API).

## Authorization (RBAC)

Method-level `@PreAuthorize` with central expressions (`security/Roles.java`); the UI hides what a role cannot
do, but the backend is authoritative.

| Capability | ADMIN | NETWORK_OPERATOR | SECURITY_ANALYST |
|---|:-:|:-:|:-:|
| View dashboard, VNFs, rules, traffic, SDN, NETCONF, alerts, settings | ✔ | ✔ | ✔ |
| VNF lifecycle, firewall rules, SDN policies/flows, NETCONF push/rollback | ✔ | ✔ | ✘ |
| Alert triage, run detection, audit log, PDF report, audit CSV | ✔ | ✘ | ✔ |
| Users, sessions, settings changes, NETCONF host-key reset | ✔ | ✘ | ✘ |

Self-protection rules: an admin cannot demote, disable or delete themselves; the last enabled admin cannot be
removed or demoted.

## Input validation & injection

* Bean Validation on every DTO (patterns, ranges, sizes) + domain validation (`Ipv4` CIDR/port parser,
  protocol/port consistency, uniqueness).
* Firewall values are **re-validated in the VNF agent**, rendered from typed fields only, and applied with
  `nft -f <file>` – no shell, no free text in the script (rule names never reach nftables; comments are numeric ids).
* JPA bound parameters / Criteria API only (no string-built SQL).
* XML: XXE-safe parser; values escaped when generating NETCONF payloads.
* CSV exports neutralise spreadsheet formulas (`=`, `+`, `-`, `@` prefixes).
* IPv4 parsing never performs DNS lookups.

## Secrets

| Secret | Source | Storage | Exposure |
|---|---|---|---|
| JWT signing key | `NEXUS_JWT_SECRET` (≥ 32 bytes) | memory | never logged / returned |
| DB password | `DB_PASSWORD` | env | – |
| Agent token | `NEXUS_AGENT_TOKEN` | env (backend + VNF env) | never returned; `/integrations/config` reports only "configured" |
| NETCONF passwords | generated per VNF (24 chars) or entered by operator | `netconf_devices.password_enc` AES-256-GCM with `NEXUS_CREDENTIAL_KEY` | never returned |
| ONOS / OSM credentials | env | memory | never returned |

`.env` is git-ignored; `.env.example` contains placeholders only. Logs never contain request headers or
credentials (the agent's access log omits headers; the backend logs exception messages, not payloads).

## Infrastructure hardening

* Backend → Docker only through **docker-socket-proxy** (containers / networks / images / version endpoints);
  the backend container never mounts the Docker socket.
* The lab orchestrator only operates on containers labelled `nexus.vnf=true` **and** `nexus.vnf.id=<id>`.
* VNF containers: `NET_ADMIN`/`NET_RAW` only, `SYS_ADMIN`/`SYS_MODULE`/`SYS_PTRACE` dropped,
  `no-new-privileges`, CPU/memory/PID limits.
* Agent and NETCONF reachable only from the management interface (nftables `input` chain) on an internal network.
* The management network `nexus-mgmt` and the backend data/control networks are `internal` (no external
  routing). The lab LAN/DMZ networks cannot be `internal`: Docker would drop traffic routed through the firewall
  VNF. Docker's inter-network isolation still prevents LAN↔DMZ traffic from bypassing the VNF, and the VNF's
  forward chain is default-deny. Only nginx (3000) and the API (127.0.0.1:8080) are published.
* HTTP security headers: CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`,
  `Permissions-Policy`; Spring Boot error details (stack traces/messages) disabled; unexpected errors return
  a reference id only.
* CORS restricted to configured origins (`NEXUS_CORS_ORIGINS`), no credentials.
* Optimistic locking on VNFs, rules, users and alerts prevents lost updates.

## Audit

Every authentication event and administrative change is written to `audit_logs` in a **separate transaction**
(failed operations are recorded even when the business transaction rolls back): user, action, category,
target, outcome, details, client IP (via `X-Forwarded-For` from the trusted proxy).

## Known limitations / production recommendations

* Terminate TLS in front of nginx (reverse proxy / ingress) and enable agent TLS (`AGENT_TLS_CERT/KEY`).
* Replace the shared agent token with mTLS per VNF for large deployments.
* The agent token is visible to anyone who can `docker inspect` a VNF container – restrict Docker access.
* Store secrets in a vault (Kubernetes secrets / HashiCorp Vault) instead of `.env` files.
* JWT revocation needs a DB lookup per request; add a cache for high request volumes.
