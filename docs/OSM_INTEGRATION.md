# OSM Integration

Open Source MANO is the production orchestrator option (`NEXUS_ORCHESTRATOR_MODE=osm`). The platform talks to the
OSM **Northbound Interface (NBI)**, an ETSI SOL005-style REST API (default `https://<osm>:9999/osm`).

> **Verification status:** implemented against the OSM NBI API reference (Release 14+). OSM requires its own VM
> (≥ 4 vCPU, 8 GB RAM, 40 GB disk) plus a VIM (OpenStack / VMware / AWS…), which is not available in this
> development environment, so the OSM adapter has **not** been executed against a live OSM here. Without OSM the
> OSM page reports "not configured" and no OSM data is ever simulated.

## Configuration

| Variable | Meaning |
|---|---|
| `OSM_NBI_URL` | e.g. `https://10.0.0.5:9999/osm` |
| `OSM_USERNAME` / `OSM_PASSWORD` / `OSM_PROJECT` | NBI credentials |
| `OSM_VIM_ACCOUNT` | VIM account id or name used for instantiation |
| `OSM_NSD_ID` | NS descriptor (default `nexus_fw-ns`) |
| `OSM_VERIFY_TLS` | `false` only for self-signed lab NBIs (logged as a warning) |

## Descriptor packages (`osm/`)

* `nexus_fw_vnfd` – SOL006 VNFD: VDU `fw-vdu` (Ubuntu 24.04 cloud image), three external CPs
  (`vnf-mgmt-ext`, `vnf-lan-ext`, `vnf-wan-ext` with port security disabled so the VNF can forward),
  monitoring parameters, scaling aspect `fw-scale-out` (max 2 extra instances), cloud-init that installs
  nftables + ulogd2 and runs the same agent as a hardened systemd service.
* `nexus_fw_nsd` – SOL006 NSD with virtual links `mgmtnet` (management), `lannet`, `wannet`.
* `build-and-onboard.sh` – validates, builds and onboards both packages with `osmclient`.

## NBI calls

| Lifecycle step | NBI call |
|---|---|
| Authenticate | `POST /admin/v1/tokens` (token cached until expiry, re-issued on 401) |
| Health / version | `GET /version` |
| Resolve NSD | `GET /nsd/v1/ns_descriptors` |
| Instantiate | `POST /nslcm/v1/ns_instances_content` → `{id, nslcmop_id}` |
| Await operation | `GET /nslcm/v1/ns_lcm_op_occs/{id}` until `COMPLETED` (or `FAILED*` → error) |
| VNF record (IPs) | `GET /nslcm/v1/vnf_instances?nsr-id-ref={nsId}` |
| Start / stop | `POST /nslcm/v1/ns_instances/{id}/operate` with `changeStateTo=start|stop` |
| Horizontal scale | `POST /nslcm/v1/ns_instances/{id}/scale` (`SCALE_OUT`/`SCALE_IN`, aspect `fw-scale-out`) |
| Vertical scale | `POST /nslcm/v1/ns_instances/{id}/verticalscale` |
| Terminate | `POST /nslcm/v1/ns_instances/{id}/terminate` (`autoremove`) |
| Catalogue views | `GET /admin/v1/vim_accounts`, `/vnfpkgm/v1/vnf_packages`, `/nsd/v1/ns_descriptors`, `/nslcm/v1/ns_instances`, `/nslcm/v1/ns_lcm_op_occs` |

Each operation blocks the lifecycle thread until OSM reports the LCM operation `COMPLETED` (timeouts: 20 min
instantiate, 10 min others); the VNF stays in its transitional state meanwhile and becomes `FAILED` on error.

## Production deployment sketch

1. Install OSM (`./install_osm.sh`) on its own VM and register a VIM (`osm vim-create …`).
2. Upload an Ubuntu 24.04 cloud image to the VIM named `ubuntu24.04`.
3. Host `vnf-firewall/agent/nexus_agent.py` and `vnf-firewall/ulogd.conf` on an internal artifact server.
4. `./osm/build-and-onboard.sh`
5. Instantiation parameters (`additionalParamsForVnf`): `agent_token`, `lan_cidr`, `wan_cidr`, `agent_url`.
6. Backend: `NEXUS_ORCHESTRATOR_MODE=osm`, OSM variables above; the backend must route to the VNF
   management network to reach the agent (port 9443) and NETCONF (830).
