#!/usr/bin/env bash
# Verifies allowed/blocked traffic through the firewall VNF in the Docker NFV lab.
# Expects the demo policy from docs/DEMO_SCENARIO.md steps 1-3 (default DROP, allow-web-client1,
# allow-ping-lan, deny-ssh).
set -uo pipefail
C1=nexus-lab-client1; C2=nexus-lab-client2; SRV=10.20.0.10
pass=0; fail=0
check() { # name expected(ALLOW|BLOCK) container command...
  local name=$1 expected=$2 ctr=$3; shift 3
  if docker exec "$ctr" "$@" >/dev/null 2>&1; then got=ALLOW; else got=BLOCK; fi
  if [[ $got == "$expected" ]]; then echo "[PASS] $name -> $got"; pass=$((pass+1)); else echo "[FAIL] $name -> $got (expected $expected)"; fail=$((fail+1)); fi
}
check "client1 HTTP :80 to server"     ALLOW $C1 curl -sf -m 3 http://$SRV/
check "client2 HTTP :80 to server"     BLOCK $C2 curl -sf -m 3 http://$SRV/
check "client1 HTTP :8080 to server"   BLOCK $C1 curl -sf -m 3 http://$SRV:8080/
check "client1 ICMP to server"         ALLOW $C1 ping -c 1 -W 2 $SRV
check "client2 ICMP to server"         ALLOW $C2 ping -c 1 -W 2 $SRV
check "client1 TCP :22 to server"      BLOCK $C1 nc -z -w 2 $SRV 22
check "client2 TCP :3306 to server"    BLOCK $C2 nc -z -w 2 $SRV 3306
echo "passed=$pass failed=$fail"
exit $fail
