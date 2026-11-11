# API Documentation

* Base path: `/api/v1` (versioned). JSON in/out. Times are ISO-8601 UTC.
* Interactive OpenAPI 3 documentation: **`/swagger-ui.html`**, machine-readable spec: **`/v3/api-docs`**.
* Authentication: `Authorization: Bearer <accessToken>` from `POST /auth/login`.
* Roles: `ANY` = any authenticated user, `OPS` = ADMIN or NETWORK_OPERATOR, `SEC` = ADMIN or SECURITY_ANALYST,
  `ADM` = ADMIN.

## Error contract

```json
{ "timestamp": "2026-11-11T08:00:00Z", "status": 409, "code": "INVALID_STATE",
  "message": "Cannot stop a VNF in state CREATED", "path": "/api/v1/vnfs/3/stop",
  "violations": [ { "field": "name", "message": "…" } ] }
```

| HTTP | code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST` | invalid input |
| 401 | `UNAUTHENTICATED`, `INVALID_CREDENTIALS` | missing/expired/revoked token, bad login |
| 403 | `FORBIDDEN` | role not permitted |
| 404 | `NOT_FOUND` | unknown resource |
| 409 | `CONFLICT`, `INVALID_STATE`, `CONCURRENT_MODIFICATION` | duplicates, illegal lifecycle transition, optimistic lock |
| 423 | `ACCOUNT_LOCKED` | lockout |
| 502 | `INFRASTRUCTURE_ERROR` | OSM / ONOS / Docker / NETCONF / agent failure (message names the system) |
| 503 | `ORCHESTRATOR_DISABLED`, `CREDENTIAL_KEY_MISSING` | feature not configured |

## Endpoints

### Authentication & users
| Method | Path | Role | Description |
|---|---|---|---|
| POST | `/auth/login` | public | `{username,password}` → `{accessToken, expiresAt, user}` |
| POST | `/auth/logout` | ANY | revoke current session |
| GET | `/auth/me` | ANY | current user |
| POST | `/auth/change-password` | ANY | revokes all own sessions |
| GET | `/auth/sessions` | ADM | active sessions |
| DELETE | `/auth/sessions/{id}` | ADM | revoke a session |
| GET/POST | `/users` | ADM | list / create |
| GET/PATCH/DELETE | `/users/{id}` | ADM | read / update (role, enabled, password reset, unlock) / delete |

### VNF lifecycle
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/vnfs` | ANY | list |
| POST | `/vnfs` | OPS | create (`CREATED`) |
| GET | `/vnfs/{id}` | ANY | details incl. latest metrics + provenance |
| PATCH | `/vnfs/{id}` | OPS | configure (description, default action) |
| POST | `/vnfs/{id}/deploy` · `/start` · `/stop` · `/restart` · `/terminate` | OPS | **202 Accepted**, transitional state; poll `GET /vnfs/{id}` |
| POST | `/vnfs/{id}/scale` | OPS | `{direction: VERTICAL|SCALE_OUT|SCALE_IN, cpuLimit, memoryLimitMb}` |
| DELETE | `/vnfs/{id}` | OPS | delete record (CREATED / TERMINATED) |
| GET | `/vnfs/{id}/events` | ANY | lifecycle history |
| GET | `/vnfs/{id}/metrics?minutes=60` | ANY | CPU / memory / throughput samples |

### Firewall
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/firewall/vnfs/{vnfId}/policy` | ANY | default action, ordered rules, desired vs applied revision |
| GET | `/firewall/vnfs/{vnfId}/runtime` | ANY | ruleset actually loaded in the VNF (`nft list`) |
| POST | `/firewall/vnfs/{vnfId}/rules` | OPS | create → `{rule, sync}` |
| GET/PUT | `/firewall/rules/{id}` | ANY / OPS | read / update → `{rule, sync}` |
| PATCH | `/firewall/rules/{id}/enabled` | OPS | `{enabled}` → `{rule, sync}` |
| DELETE | `/firewall/rules/{id}` | OPS | → `sync` |
| POST | `/firewall/vnfs/{vnfId}/rules/reorder` | OPS | `{ruleIds:[…]}` renumbers 10, 20, … |
| POST | `/firewall/vnfs/{vnfId}/apply` | OPS | force push |

`sync = {attempted, applied, status, desiredRevision, appliedRevision, message}` – `applied:false` always
explains why the change is not (yet) enforced.

### Traffic, alerts, audit
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/traffic/summary?minutes=&vnfId=` | ANY | totals + data sources |
| GET | `/traffic/timeseries?minutes=&bucketSeconds=` | ANY | bucketed counter deltas |
| GET | `/traffic/top?minutes=&limit=` | ANY | top sources / blocked sources / destinations / ports / protocols |
| GET | `/traffic/events?from&to&vnfId&action&protocol&srcIp&dstIp&port&direction&page&size` | ANY | search |
| GET | `/traffic/events/export` | ANY | CSV (same filters) |
| GET | `/alerts?status&severity&detector&sourceIp&from&to&page&size` | ANY | search |
| GET | `/alerts/stats` · `/alerts/{id}` · `/alerts/export` | ANY | stats / detail / CSV |
| PATCH | `/alerts/{id}` | SEC | `{status, assignedTo, note}` (note required to resolve/dismiss) |
| POST | `/alerts/detect` | SEC | run detection now |
| GET | `/audit-logs?from&to&username&action&category&outcome&search` | SEC | search |
| GET | `/audit-logs/actions` · `/audit-logs/export` | SEC | action list / CSV |
| GET | `/reports/security.pdf?hours=24` | SEC | PDF security report |

### SDN / OpenFlow, OSM, NETCONF, platform
| Method | Path | Role | Description |
|---|---|---|---|
| GET | `/sdn/status` · `/sdn/devices` · `/sdn/devices/{id}/ports` · `/sdn/hosts` · `/sdn/links` · `/sdn/flows?deviceId=` | ANY | ONOS inventory |
| DELETE | `/sdn/flows/{deviceId}/{flowId}` | OPS | remove flow |
| GET/POST | `/sdn/policies` | ANY / OPS | network security policies (compiled to OpenFlow) |
| POST | `/sdn/policies/{id}/reinstall` · DELETE `/sdn/policies/{id}` | OPS | |
| GET | `/sdn/topology?view=sdn|nfv` | ANY | graph |
| GET | `/osm/status` · `/osm/vim-accounts` · `/osm/vnf-packages` · `/osm/ns-descriptors` · `/osm/ns-instances` · `/osm/operations` | ANY | OSM NBI views |
| GET/POST | `/netconf/devices` | ANY / OPS | registry |
| POST | `/netconf/devices/{id}/test` · `/validate` · `/push` · `/snapshot` | OPS | NETCONF operations |
| GET | `/netconf/devices/{id}/running` · `/desired` · `/sync` · `/versions` · `/versions/{v}` | ANY | |
| POST | `/netconf/devices/{id}/versions/{v}/rollback` | OPS | |
| POST | `/netconf/devices/{id}/reset-host-key` | ADM | |
| GET | `/dashboard/summary?minutes=` · `/dashboard/activity` | ANY | posture & KPIs |
| GET | `/integrations` · `/integrations/config` | ANY | health, non-secret config |
| POST | `/integrations/check` | OPS | probe now |
| GET / PUT | `/settings` · `/settings/{key}` | ANY / ADM | thresholds & retention |
| GET | `/actuator/health` | public | liveness/readiness |

## Example session (curl)

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<NEXUS_ADMIN_PASSWORD>"}' | jq -r .accessToken)
H="Authorization: Bearer $TOKEN"
curl -s -H "$H" -H 'Content-Type: application/json' localhost:8080/api/v1/vnfs \
  -d '{"name":"fw-edge-01","vnfType":"FIREWALL","cpuLimit":0.5,"memoryLimitMb":256}'
curl -s -X POST -H "$H" localhost:8080/api/v1/vnfs/1/deploy
curl -s -H "$H" -H 'Content-Type: application/json' localhost:8080/api/v1/firewall/vnfs/1/rules \
  -d '{"name":"allow-web","priority":10,"action":"ALLOW","protocol":"TCP","sourceCidr":"10.10.0.11","destinationCidr":"10.20.0.10","destinationPort":"80"}'
```

A Postman collection is provided in `docs/postman/NEXUS-NFV.postman_collection.json`.
