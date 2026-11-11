# ONOS Integration

## Modes (explicit – never an automatic fallback)

| `NEXUS_ONOS_MODE` | Adapter | Data source | Behaviour |
|---|---|---|---|
| `real` (default in `application.yml`) | `RestOnosGateway` | `LIVE` | ONOS northbound REST (`ONOS_URL`, basic auth). Unreachable controller ⇒ errors are shown as such. |
| `demo` (default in `.env.example`, because a laptop rarely runs ONOS) | `DemoOnosGateway` | `DEMO` | Fixture `classpath:demo/onos-demo-topology.json` mirroring `lab/sdn/nexus_topology.py`; flows installed by policies live in memory only. Every page shows a purple **DEMO MODE** banner. |

## REST calls used (`onos/v1`)

| Purpose | Call |
|---|---|
| Controller identity / version | `GET /cluster/local` |
| Applications | `GET /applications` |
| Devices (OVS switches) | `GET /devices` |
| Ports | `GET /devices/{id}/ports` |
| Port statistics | `GET /statistics/ports/{id}` |
| Hosts | `GET /hosts` |
| Links | `GET /links` |
| Flow rules | `GET /flows`, `GET /flows/{deviceId}` |
| Install flow | `POST /flows/{deviceId}?appId=org.nexus.nfv` → `201 Location: …/flows/{deviceId}/{flowId}` |
| Remove flow | `DELETE /flows/{deviceId}/{flowId}` |

Credentials are only in the backend environment; responses to the browser never include them.

## Inventory persistence

Every inventory read upserts `network_devices`, `network_hosts`, `topology_links` with `data_source`, so the
database keeps the last known SDN topology (and DEMO rows never mix with LIVE rows – the unique keys include
the data source).

## Topology graph

`GET /api/v1/sdn/topology?view=sdn` builds a graph: controller → switches (`CONTROL` edges, OpenFlow channel),
switch ↔ switch (`LINK`, de-duplicated because ONOS reports both directions), host → switch (`ACCESS`).
Hosts whose IP belongs to a firewall VNF are drawn as `FIREWALL`.
`view=nfv` shows the NFV lab segments and attached containers from the Docker Engine network inventory.

## Lab with a real controller (Ubuntu host)

```bash
sudo lab/sdn/setup-ubuntu-sdn-lab.sh          # installs Mininet + OVS, runs onosproject/onos:2.7-latest
sudo ONOS_IP=127.0.0.1 NEXUS_AGENT_TOKEN=… python3 lab/sdn/nexus_topology.py --test
```

Backend settings: `NEXUS_ONOS_MODE=real`, `ONOS_URL=http://<host>:8181/onos/v1`, `ONOS_USERNAME=onos`,
`ONOS_PASSWORD=<password>`. The compose file also has an optional `sdn` profile that runs ONOS next to the app
(needs ~2 GB RAM; Mininet itself requires a Linux host with the openvswitch kernel module, which Docker Desktop
does not provide).

## Verification status

* REST client and flow compiler: unit/integration tested (`DomainLogicTest.OpenFlowTranslation`,
  `PlatformIntegrationTest.sdnDemoModeIsLabelledAndPoliciesCompileToFlows`).
* Against a running ONOS: see [TESTING_GUIDE.md](TESTING_GUIDE.md) for what was executed in this environment.
