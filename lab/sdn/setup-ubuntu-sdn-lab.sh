#!/usr/bin/env bash
# Installs the SDN lab prerequisites on Ubuntu 22.04 / 24.04 and starts ONOS in Docker.
#   sudo ./setup-ubuntu-sdn-lab.sh
# Afterwards:  sudo ONOS_IP=127.0.0.1 NEXUS_AGENT_TOKEN=<token> python3 lab/sdn/nexus_topology.py
set -euo pipefail

if [[ $EUID -ne 0 ]]; then echo "run as root (sudo)"; exit 1; fi

apt-get update
apt-get install -y --no-install-recommends mininet openvswitch-switch nftables ulogd2 ulogd2-json curl python3 docker.io
systemctl enable --now openvswitch-switch docker

if ! docker ps --format '{{.Names}}' | grep -q '^onos$'; then
  docker run -d --name onos --restart unless-stopped \
    -p 8181:8181 -p 8101:8101 -p 6653:6653 \
    -e ONOS_APPS=drivers,openflow,fwd,hostprovider,lldpprovider,gui2 \
    onosproject/onos:2.7-latest
fi

echo "Waiting for ONOS REST API…"
for i in $(seq 1 60); do
  if curl -sf -u onos:rocks http://127.0.0.1:8181/onos/v1/applications >/dev/null; then echo "ONOS is up"; break; fi
  sleep 5
done

cat <<'EOF'

ONOS is running (GUI: http://<host>:8181/onos/ui  user onos / rocks – change it for anything but a lab).
Configure the NEXUS backend:
  NEXUS_ONOS_MODE=real
  ONOS_URL=http://<host>:8181/onos/v1
  ONOS_USERNAME=onos
  ONOS_PASSWORD=rocks
Then start the topology:
  sudo ONOS_IP=127.0.0.1 NEXUS_AGENT_TOKEN=<token> python3 lab/sdn/nexus_topology.py
EOF
