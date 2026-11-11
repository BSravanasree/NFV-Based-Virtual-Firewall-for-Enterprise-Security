#!/usr/bin/env bash
# Packages and onboards the NEXUS descriptors into OSM using the official osmclient.
#   export OSM_HOSTNAME=<osm-nbi-host>   (osmclient)
#   ./osm/build-and-onboard.sh
set -euo pipefail
cd "$(dirname "$0")"
command -v osm >/dev/null || { echo "osmclient not installed (https://osm.etsi.org/docs/user-guide/)"; exit 1; }

osm package-validate nexus_fw_vnfd
osm package-validate nexus_fw_nsd
osm package-build nexus_fw_vnfd
osm package-build nexus_fw_nsd
osm vnfpkg-create nexus_fw_vnfd.tar.gz
osm nspkg-create nexus_fw_nsd.tar.gz
osm vnfpkg-list | grep nexus_fw
osm nspkg-list | grep nexus_fw
echo "Onboarded. Set OSM_NSD_ID=nexus_fw-ns, OSM_VIM_ACCOUNT=<vim> and NEXUS_ORCHESTRATOR_MODE=osm for the backend."
