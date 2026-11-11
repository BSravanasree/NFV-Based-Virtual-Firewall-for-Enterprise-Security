#!/usr/bin/env python3
"""
NEXUS SDN lab – Mininet + Open vSwitch (OpenFlow 1.3) controlled by ONOS.

    client1 (10.0.1.10) ─┐                                  ┌─ server (10.0.2.10)
    client2 (10.0.1.11) ─┤ s1 (LAN) ── fw VNF (router) ── s2 (DMZ) ┤
                         └────────── s1:4 ─── s2:3 ──────────┘   (inter-switch link, no L3 path)

  * s1 / s2 are OVS bridges managed by ONOS over OpenFlow 1.3 (tcp:<ONOS_IP>:6653).
  * fw is a Linux router running the same nftables ruleset/agent as the NEXUS firewall VNF:
      fw-eth0 10.0.1.1/24 (LAN), fw-eth1 10.0.2.1/24 (DMZ), fw-eth2 192.168.100.2/24 (management).
  * Hosts use the firewall as default gateway, so every LAN<->DMZ packet traverses the VNF.
  * The management link connects the firewall to the root namespace (192.168.100.1) so the NEXUS backend
    running on this host can reach the agent at http://192.168.100.2:9443.

Usage (Ubuntu 22.04/24.04, root):
    sudo ONOS_IP=127.0.0.1 NEXUS_AGENT_TOKEN=... python3 nexus_topology.py            # interactive CLI
    sudo ONOS_IP=127.0.0.1 NEXUS_AGENT_TOKEN=... python3 nexus_topology.py --test     # run verification and exit
"""
import os
import sys
import time

from mininet.cli import CLI
from mininet.link import TCLink
from mininet.log import info, setLogLevel
from mininet.net import Mininet
from mininet.node import Node, OVSSwitch, RemoteController

AGENT = os.environ.get("NEXUS_AGENT_PATH", os.path.abspath(os.path.join(os.path.dirname(__file__),
                                                                         "../../vnf-firewall/agent/nexus_agent.py")))


class LinuxRouter(Node):
    def config(self, **params):
        super().config(**params)
        self.cmd("sysctl -qw net.ipv4.ip_forward=1")
        self.cmd("sysctl -qw net.ipv4.conf.all.rp_filter=0")

    def terminate(self):
        self.cmd("sysctl -qw net.ipv4.ip_forward=0")
        super().terminate()


def build(onos_ip: str) -> Mininet:
    net = Mininet(controller=None, switch=OVSSwitch, link=TCLink, autoSetMacs=False, build=False)
    net.addController("onos", controller=RemoteController, ip=onos_ip, port=6653)
    s1 = net.addSwitch("s1", dpid="0000000000000001", protocols="OpenFlow13")
    s2 = net.addSwitch("s2", dpid="0000000000000002", protocols="OpenFlow13")

    c1 = net.addHost("client1", ip="10.0.1.10/24", mac="00:00:00:00:01:0a", defaultRoute="via 10.0.1.1")
    c2 = net.addHost("client2", ip="10.0.1.11/24", mac="00:00:00:00:01:0b", defaultRoute="via 10.0.1.1")
    srv = net.addHost("server", ip="10.0.2.10/24", mac="00:00:00:00:02:0a", defaultRoute="via 10.0.2.1")
    fw = net.addHost("fw", cls=LinuxRouter, ip="10.0.1.1/24", mac="00:00:00:00:01:01")

    net.addLink(c1, s1, port2=1)
    net.addLink(c2, s1, port2=2)
    net.addLink(fw, s1, intfName1="fw-eth0", port2=3)
    net.addLink(fw, s2, intfName1="fw-eth1", port2=1, params1={"ip": "10.0.2.1/24"})
    net.addLink(srv, s2, port2=2)
    net.addLink(s1, s2, port1=4, port2=3)

    # management link firewall <-> root namespace
    root = Node("root", inNamespace=False)
    link = net.addLink(root, fw, intfName2="fw-eth2")
    net.build()
    link.intf1.setIP("192.168.100.1/24")
    fw.intf("fw-eth2").setIP("192.168.100.2/24")
    fw.intf("fw-eth1").setMAC("00:00:00:00:02:01")
    return net


def start_services(net: Mininet):
    fw, srv = net.get("fw"), net.get("server")
    token = os.environ.get("NEXUS_AGENT_TOKEN", "")
    if len(token) >= 16 and os.path.exists(AGENT):
        os.makedirs("/tmp/nexus-mininet-agent", exist_ok=True)
        fw.cmd(f"NEXUS_AGENT_TOKEN='{token}' NEXUS_LAN_CIDR=10.0.1.0/24 NEXUS_WAN_CIDR=10.0.2.0/24 "
               f"AGENT_STATE_DIR=/tmp/nexus-mininet-agent AGENT_EVENT_FILE=/tmp/nexus-mininet-agent/events.json "
               f"python3 -u {AGENT} > /tmp/nexus-mininet-agent/agent.log 2>&1 &")
        info("*** NEXUS agent started in fw namespace (http://192.168.100.2:9443)\n")
    else:
        info("*** NEXUS_AGENT_TOKEN not set or agent missing: firewall starts with a static demo ruleset\n")
        fw.cmd("nft -f - <<'EOF'\n"
               "table inet nexus_fw { chain forward { type filter hook forward priority filter; policy drop;\n"
               " ct state established,related accept\n"
               " ip saddr 10.0.1.10 ip daddr 10.0.2.10 tcp dport 80 accept\n"
               " ip saddr 10.0.1.0/24 icmp type echo-request accept } }\nEOF")
    srv.cmd("python3 -m http.server 80 >/tmp/nexus-srv-80.log 2>&1 &")
    srv.cmd("python3 -m http.server 22 >/tmp/nexus-srv-22.log 2>&1 &")  # stand-in service on the SSH port


def verify(net: Mininet) -> bool:
    """Allowed/blocked traffic checks through the firewall VNF."""
    c1, c2 = net.get("client1"), net.get("client2")
    checks = [
        ("client1 -> server HTTP (allowed)", c1, "curl -s -m 3 -o /dev/null -w '%{http_code}' http://10.0.2.10/", "200"),
        ("client2 -> server HTTP (blocked by default-deny)", c2, "curl -s -m 3 -o /dev/null -w '%{http_code}' http://10.0.2.10/", "000"),
        ("client1 -> server port 22 (blocked)", c1, "curl -s -m 3 -o /dev/null -w '%{http_code}' http://10.0.2.10:22/", "000"),
    ]
    ok = True
    for name, host, cmd, expected in checks:
        out = host.cmd(cmd).strip()[-3:]
        passed = out == expected
        ok &= passed
        print(f"[{'PASS' if passed else 'FAIL'}] {name}: got {out}, expected {expected}")
    return ok


if __name__ == "__main__":
    setLogLevel("info")
    onos = os.environ.get("ONOS_IP", "127.0.0.1")
    network = build(onos)
    network.start()
    start_services(network)
    info("*** waiting for ONOS to learn the topology\n")
    time.sleep(8)
    network.pingAll(timeout="1")  # lets ONOS hostprovider discover hosts (firewall rules may drop some pings)
    if "--test" in sys.argv:
        result = verify(network)
        network.stop()
        sys.exit(0 if result else 1)
    CLI(network)
    network.stop()
