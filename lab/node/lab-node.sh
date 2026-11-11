#!/usr/bin/env bash
# ROLE=client: route the protected segment through the firewall VNF.
# ROLE=server: route replies back through the firewall VNF and start demo services.
set -euo pipefail

if [[ -n "${ROUTE_CIDR:-}" && -n "${ROUTE_VIA:-}" ]]; then
  # replace any existing route; the gateway (firewall VNF) may not exist yet, that's fine (on-link)
  ip route replace "${ROUTE_CIDR}" via "${ROUTE_VIA}"
  echo "route ${ROUTE_CIDR} via ${ROUTE_VIA} installed"
fi

case "${ROLE:-client}" in
  server)
    exec python3 -u /opt/lab/lab_services.py
    ;;
  *)
    echo "client ready – use: docker exec -it $(hostname) bash"
    exec sleep infinity
    ;;
esac
