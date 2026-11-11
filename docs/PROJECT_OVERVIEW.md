# Project Overview

## Problem

Small and medium enterprises need perimeter and segment firewalls but hardware appliances are costly,
hard to scale and managed box by box. Network Functions Virtualisation (NFV) replaces the appliance with
software running on commodity compute, and Software-Defined Networking (SDN) makes traffic steering
programmable.

## Solution

NEXUS NFV is a management platform that treats a firewall as a **Virtual Network Function (VNF)**:

1. **Deploy** – an operator creates a firewall VNF and deploys it; the platform instantiates an Ubuntu
   firewall (container in the local NFV lab, VM through OSM in production) attached to management, LAN and
   DMZ networks.
2. **Configure** – firewall rules are defined centrally (desired state). Every change is pushed to the
   VNF's agent, validated and applied atomically to nftables, or committed through NETCONF to the VNF's
   YANG datastore.
3. **Operate** – start, stop, restart, scale and terminate VNFs through a lifecycle state machine whose
   transitions are confirmed by the infrastructure.
4. **Observe** – exact nftables counters, connection events (NFLOG), CPU/memory/throughput of each VNF.
5. **Detect** – rule-based detectors (port scan, repeated blocks, brute force on sensitive ports, unusual
   frequency, unexpected flows) raise de-duplicated alerts with a triage workflow.
6. **Steer** – network security policies are compiled into OpenFlow rules and installed on Open vSwitch
   through ONOS, e.g. to drop traffic at the switch or steer it into the firewall VNF.
7. **Govern** – JWT authentication, three roles, full audit trail, CSV and PDF reports.

## Users and roles

| Role | Can |
|---|---|
| `ADMIN` | everything, including user management, sessions and settings |
| `NETWORK_OPERATOR` | VNF lifecycle, firewall rules, SDN policies, NETCONF operations |
| `SECURITY_ANALYST` | read all infrastructure data, triage alerts, read audit logs, export reports |

## Honest scope statement

* The **Docker NFV lab** is a real deployment target (real containers, real kernels' netfilter, real traffic),
  sized for a laptop. It is labelled `LAB` everywhere in the UI.
* **OSM** integration is implemented against the OSM NBI API but was not executed against a live OSM in
  this environment; it reports "not configured" rather than inventing data.
* **ONOS** integration uses the real REST API. When no controller is available the platform must be
  explicitly put in `DEMO` mode, and every SDN view then carries a purple `DEMO` badge and banner.
* Detection is **threshold-based and transparent**, not machine learning.
* Telemetry is **counters + packet headers**. The platform performs no payload / deep packet inspection.

See [SYSTEM_ARCHITECTURE.md](SYSTEM_ARCHITECTURE.md) for the design.
