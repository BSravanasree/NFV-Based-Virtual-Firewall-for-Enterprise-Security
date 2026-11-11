# OpenFlow & Open vSwitch Integration

The platform manages Open vSwitch **through the SDN controller** (ONOS speaks OpenFlow 1.3 to OVS), which is
the standard SDN separation of concerns: the application never talks OpenFlow directly.

## Capabilities

| Capability | Where |
|---|---|
| Switch discovery | ONOS devices (`protocol=OF_13`, `driver=ovs`) – OpenFlow page / ONOS page |
| OpenFlow connectivity | device `available`, control-channel address (`channelId`) |
| Port information & statistics | `/sdn/devices/{id}/ports` (state, speed, MAC, RX/TX packets/bytes, drops) |
| Flow table | `/sdn/flows` with match / actions / packet & byte counters / owning app |
| Flow management | delete any flow (with warning for flows owned by other ONOS apps) |
| Security policies → flows | `/sdn/policies` (create, reinstall, delete) |
| Topology visualisation | Network Topology page (`view=sdn`) |

## Policy compilation (`FlowTranslator`)

| Policy field | OpenFlow match / instruction |
|---|---|
| always | `ETH_TYPE=0x0800` |
| protocol TCP / UDP / ICMP | `IP_PROTO=6 / 17 / 1` |
| source / destination CIDR | `IPV4_SRC` / `IPV4_DST` (normalised, IPv4) |
| destination port | `TCP_DST` or `UDP_DST` (rejected for ICMP/ANY) |
| action `DROP` | `NOACTION` (no output → packet dropped) |
| action `ALLOW` | `OUTPUT:NORMAL` (OVS normal L2 pipeline) |
| action `FORWARD` | `OUTPUT:<port>` |
| action `STEER_TO_FIREWALL` | `OUTPUT:<firewall VNF port>` – service chaining into the VNF |

Flows are permanent, priority 1000–60000 (above ONOS reactive forwarding at 10), owned by app
`org.nexus.nfv`. The ONOS flow id is stored on the policy so deletion removes exactly that flow.

## Reference test topology (`lab/sdn/nexus_topology.py`)

```
client1 10.0.1.10 ─┐                                   ┌─ server 10.0.2.10
client2 10.0.1.11 ─┤ s1 (OVS, of:…01) ─ fw VNF ─ s2 (OVS, of:…02) ┤
                   └───────── s1:4 ════ s2:3 ─────────┘
                ONOS controller (tcp:6653, OpenFlow 1.3)
```

* Clients' default gateway is the firewall (10.0.1.1), the server's is 10.0.2.1 → every LAN↔DMZ packet is
  routed **through the firewall VNF** (nftables `forward` hook).
* `--test` runs allowed/blocked checks (HTTP allowed from client1, blocked from client2, port 22 blocked).
* Example OpenFlow policy enforced *at the switch*:
  `DROP TCP 10.0.1.11/32 → any :22 on of:0000000000000001` – packets never reach the firewall.

## Docker NFV lab equivalent

On Docker Desktop (no OVS kernel module) the same chain is built with Linux bridges:
`nexus-lan` ↔ firewall VNF ↔ `nexus-dmz`; clients/server route through the VNF via static routes. This is
what the end-to-end tests exercise on this machine.
