# Testing Guide

## Automated tests

```bash
cd backend  && mvn test     # 51 tests
cd frontend && npm test     # 27 tests
```

### Backend (JUnit 5, Mockito, Spring MockMvc, H2 in MySQL mode with the production Flyway migrations)

| Suite | Type | Covers |
|---|---|---|
| `DomainLogicTest.VnfStateMachine` | unit | allowed/illegal lifecycle transitions, failure & recovery paths |
| `DomainLogicTest.Ipv4Validation` | unit (parameterised) | CIDR normalisation, host-bit rejection, injection strings, DNS names, IPv6, port ranges |
| `DomainLogicTest.OpenFlowTranslation` | unit | policy → ONOS flow JSON (match fields, NOACTION drop, steering port, invalid combinations) |
| `DomainLogicTest.YangCodecAndAgentContract` | unit | YANG XML encoding/escaping, order-independent checksums, agent ruleset (enabled only, priority order) |
| `DomainLogicTest.CsvExport` | unit | CSV escaping and formula-injection neutralisation |
| `DomainLogicTest.TelemetryMath` | unit | Docker CPU/memory computation (same as `docker stats`), counter deltas across table re-creation |
| `FirewallRuleServiceTest` | Mockito | normalisation, revision bump, audit call, ICMP+port rejection, duplicate priority, terminated VNF, injection never persisted |
| `PlatformIntegrationTest` | Spring Boot + MockMvc + stub agent (real HTTP) | 401 without/with invalid token; generic bad-credential errors + audit; lockout after 5 failures (423); logout revokes the JWT; RBAC matrix (analyst/operator/admin); weak passwords; VNF validation & illegal transitions; **deploy fails honestly (FAILED, never ACTIVE) when Docker is unreachable**; rules on non-active VNF stored but not enforced; push to agent with token, confirmed revision → `IN_SYNC`; disable/reorder re-apply; unreachable agent → `FAILED`, "not enforced"; port-scan and brute-force detection + de-duplication; alert resolution requires a note; SDN DEMO labelling and policy → flow; PDF/CSV/OpenAPI; secrets never returned |

### Frontend (Vitest + React Testing Library + jsdom)

| Suite | Covers |
|---|---|
| `logic.test.ts` | rule form validation (CIDR, ports, ICMP+port, duplicate priority, injection), plain-language rule description, password policy, formatters, lifecycle actions per state, topology layering, XML pretty printer |
| `ui-phase2.test.tsx` | landing page sections and honest integration labels, Launch Console routing, docs status table, security-event bucketing, global search (pages + IP pivots), drawer Escape, animated numbers under reduced motion |
| `components.test.tsx` | badges & provenance tooltips, confirm dialog, redirect to login, client-side validation, backend error display, login + role-based navigation (analyst cannot see User Management), dashboard KPIs with LAB/DEMO badges, time-window reload, error state with retry |

## Infrastructure tests

| Test | How |
|---|---|
| VNF agent contract & hardening | `vnf-firewall` smoke test: 401 without token, invalid ruleset 400, injection 400, atomic apply, stale revision 409, counters, rendered ruleset |
| Allowed/blocked traffic | `lab/verify-traffic.sh` / `lab/verify-traffic.ps1` after DEMO_SCENARIO steps 1–3 |
| Detection | `nmap` / `nc` loops from `client2` (DEMO_SCENARIO step 5) |
| NETCONF | NETCONF page: test, get-config, push, sync, rollback against the VNF's Netopeer2 |
| OpenFlow policy behaviour | `sudo python3 lab/sdn/nexus_topology.py --test` on an Ubuntu host with ONOS |
| API | Postman collection `docs/postman/NEXUS-NFV.postman_collection.json` (run "Login" first) |

## End-to-end test (`lab/e2e_test.py`)

Repeatable, stdlib-only script that drives the public REST API and checks real traffic from the lab containers:

```bash
docker compose --profile lab up -d
python lab/e2e_test.py          # reads the admin password from .env; exit code = failed checks
```

It covers: deploy → ACTIVE (confirmed), default-deny in the data path, rule enforcement, 7 allowed/blocked
traffic cases, enable/disable, runtime `nft` ruleset, `nmap` port scan + brute force → NFLOG events, counters,
rule hit counters, 4 detectors, Docker metrics, NETCONF (session, get-config, sync, validate, push, rollback
enforced in the data path, drift), vertical scaling, refusal of horizontal scaling in the lab, restart with policy
persistence, stop (no forwarding) / start, RBAC, audit, PDF report, terminate and cleanup.

## Execution log – public demo (Cloudflare Quick Tunnel)

| Check (against the generated `https://*.trycloudflare.com` URL) | Result |
|---|---|
| Landing, docs, login, console routes, static assets, `/actuator/health` | 200; anonymous `/api/v1/auth/me` → 401 |
| Plain `http://` | 308 → `https://` |
| `python lab/security_audit.py` with `NEXUS_BASE=<public URL>` | **19 / 19** (87/87 API operations reject anonymous calls, no secrets in bundle, only nginx published) |
| `python lab/e2e_test.py` with `NEXUS_API=<public URL>/api/v1` | **53 / 53** (VNF deployed and traffic allowed/blocked through the public API) |
| Chromium sweep (admin desktop + mobile, demo analyst) | 45 page loads / 620 requests: no CORS, mixed content, console or 5xx errors; analyst sees no lifecycle/rule controls, `POST /vnfs` → 403, `/users` denied |

Defect found before exposure and fixed: behind the tunnel the browser's `Origin` (`https://…trycloudflare.com`)
did not match the request as seen by the backend (`http://backend:8080`), so Spring's CORS filter rejected
logins with 403. nginx now forwards the edge's scheme/host/port; foreign origins are still rejected.

## Execution log – Phase 2 (UI redesign + production path)

| Test | Result |
|---|---|
| Backend `mvn test` | **51 / 51 passed** |
| Frontend `npm test` | **27 / 27 passed** (19 existing unchanged + 8 new); `tsc` clean; production build OK (initial JS 293 KB / 94 KB gzip after route code-splitting) |
| `lab/e2e_test.py` (now 53 checks) | **53 / 53** against the dev stack and **53 / 53 through the HTTPS production path** (`deploy/docker-compose.prod.yml` + Caddy, `https://localhost`) |
| Browser sweep (Playwright/Chromium, 1440 px and 390 px, all 16 console pages + public site) | no console errors, no page errors, no 5xx responses, no horizontal overflow |
| Production path | HTTP→HTTPS 308, HSTS/CSP/X-Frame-Options present, `/actuator/health` = `{"status":"UP"}`, API and UI ports not published |

Defects found and fixed in Phase 2: scroll-reveal hid landing content when no scroll events occur (switched to
CSS scroll-driven animation); overlays too translucent for form readability; NETCONF devices of terminated VNFs
lingered as "unreachable" integrations (now removed on terminate; new E2E check); Caddy refused an empty
ACME e-mail (directive removed); `/actuator/health` was not proxied for public health checks.

## Execution log – Phase 1

Windows 11, Docker Desktop 29.4 (WSL2 kernel 6.6.87), 8 vCPU / 3.9 GB for Docker, Java 21, Node 24 — 2026-11-11.

| Test | Result |
|---|---|
| Backend `mvn test` | **51 / 51 passed** |
| Frontend `npm test` | **19 / 19 passed**; `tsc` and `vite build` clean |
| VNF agent smoke test | 401 without token · invalid ruleset 400 · `"10.0.0.0/8; flush ruleset"` 400 · atomic apply · stale revision 409 · counters · rendered ruleset ✔ |
| NFLOG → ulogd2 JSON in a container | ✔ (events with src/dst/ports/protocol) |
| Netopeer2 in the VNF | listening on 830, `nexus-firewall` module installed, REST policy mirrored to the running datastore ✔ |
| `lab/e2e_test.py` against the compose stack (MySQL 8.4) | **52 / 52 checks passed** (VNF ACTIVE in ≈6 s) |
| UI via nginx | index + SPA deep links 200, API proxied, security headers on all routes, audited client IP = real client |

Sample numbers from the passing run: 132 blocked NFLOG events from the scanning client, counters
allowed = 18 / blocked = 137 packets, rule hits `deny-ssh=3, allow-web-client1=1, allow-ping-lan=3`, detectors
`PORT_SCAN, REPEATED_BLOCKED, UNAUTHORIZED_ACCESS, UNEXPECTED_COMMUNICATION`, VNF memory ≈17 MiB.

### Defects found by the end-to-end run (and fixed)

| Defect | Root cause | Fix / regression protection |
|---|---|---|
| Every policy push failed with HTTP 400 from the agent | JDK `HttpClient` streams request bodies with chunked encoding; Python `http.server` needs `Content-Length` | `BufferingClientHttpRequestFactory` in `FirewallAgentClient`; agent now answers 411 with an explicit message; the integration-test stub agent rejects chunked bodies |
| Traffic routed via the VNF never arrived | Docker `internal: true` networks drop bridged packets whose destination is outside the network's subnet (exactly the service-chained flow) | LAN/DMZ lab networks are not `internal` (management network still is) |
| Default Spring in-memory user with generated password in the log | `UserDetailsServiceAutoConfiguration` | explicit `UserDetailsService` bean that never authenticates |
| Security headers missing on UI routes | nginx drops server-level `add_header` when a location adds its own | headers in an include used by every UI location |
| NETCONF XML viewer broke leaf elements | pretty-printer bug (found by a unit test) | tokenizer rewrite |

### Not executed in this environment (honest status)

| Item | Why | What exists |
|---|---|---|
| OSM adapter against a live OSM | needs a dedicated OSM VM + VIM | adapter, descriptors, onboarding script, unit-tested NBI flow shapes |
| ONOS REST adapter against a live controller | ONOS (2 GB) + Mininet require a Linux host with the OVS kernel module | REST client, DEMO fixture shaped like ONOS responses, `lab/sdn/` scripts |
| `lab/sdn/nexus_topology.py --test` | Mininet cannot run inside Docker Desktop | script for an Ubuntu host |
| `lab/verify-traffic.sh` / `.ps1` | superseded by `e2e_test.py`, which performs the same checks | quick manual check after following DEMO_SCENARIO steps 1–3 |
