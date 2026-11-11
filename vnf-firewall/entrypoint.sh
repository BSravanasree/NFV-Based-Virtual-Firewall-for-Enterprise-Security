#!/usr/bin/env bash
# NEXUS firewall VNF entrypoint: ulogd2 (NFLOG -> JSON), optional Netopeer2, then the agent (foreground).
set -euo pipefail

mkdir -p /var/log/ulog /var/lib/nexus-agent
sysctl -q net.ipv4.ip_forward 2>/dev/null | grep -q "= 1" || echo "WARNING: net.ipv4.ip_forward is not enabled; the VNF will not forward traffic" >&2

# Packet event logging
ulogd -c /etc/nexus-agent/ulogd.conf -d || echo "WARNING: ulogd failed to start; packet events unavailable" >&2

# NETCONF server (only in the netconf-enabled image and when the orchestrator supplied a credential)
if [[ "${NETCONF_ENABLED:-false}" == "true" ]]; then
  if [[ -x /usr/local/sbin/netopeer2-server ]]; then
    if /opt/nexus-agent/init-netconf.sh; then
      /usr/local/sbin/netopeer2-server -d -v1 >/var/log/netopeer2.log 2>&1 &
      echo "netopeer2-server started on :830"
    else
      export NETCONF_ENABLED=false
    fi
  else
    echo "WARNING: NETCONF requested but this image was built without Netopeer2" >&2
    export NETCONF_ENABLED=false
  fi
fi

# Never pass secrets on the command line; the agent reads its token from the environment.
exec python3 -u /opt/nexus-agent/nexus_agent.py
