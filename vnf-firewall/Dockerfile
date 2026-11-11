# syntax=docker/dockerfile:1
# =============================================================================
# NEXUS firewall VNF – Ubuntu 24.04 + nftables + ulogd2 + agent (+ Netopeer2)
#
#   docker build --target fw-base -t nexus/fw-vnf:1.0.0-base vnf-firewall    # packet filter only
#   docker build -t nexus/fw-vnf:1.0.0 vnf-firewall                            # + NETCONF (default)
# =============================================================================

# ---------------------------------------------------------------- base VNF
FROM ubuntu:24.04 AS fw-base
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      nftables ulogd2 ulogd2-json iproute2 python3 tini ca-certificates procps \
 && rm -rf /var/lib/apt/lists/*

COPY ulogd.conf /etc/nexus-agent/ulogd.conf
COPY agent/nexus_agent.py /opt/nexus-agent/nexus_agent.py
COPY entrypoint.sh /usr/local/bin/nexus-vnf-entrypoint
RUN sed -i 's/\r$//' /usr/local/bin/nexus-vnf-entrypoint /opt/nexus-agent/nexus_agent.py /etc/nexus-agent/ulogd.conf \
 && chmod 0755 /usr/local/bin/nexus-vnf-entrypoint \
 && chmod 0644 /opt/nexus-agent/nexus_agent.py \
 && mkdir -p /var/lib/nexus-agent /var/log/ulog \
 && chmod 0700 /var/lib/nexus-agent

LABEL org.opencontainers.image.title="nexus-fw-vnf" \
      org.opencontainers.image.description="NEXUS NFV firewall VNF (nftables)" \
      nexus.vnf.type="FIREWALL"
EXPOSE 9443
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s \
  CMD nft list table inet nexus_fw >/dev/null 2>&1 || exit 1
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/nexus-vnf-entrypoint"]

# ---------------------------------------------------------------- NETCONF build stage
FROM ubuntu:24.04 AS netconf-build
ARG DEBIAN_FRONTEND=noninteractive
ARG LIBYANG_REF=v6.4.13
ARG LIBNETCONF2_REF=v4.6.4
ARG SYSREPO_REF=v5.2.27
ARG NETOPEER2_REF=92e18139e996fe9333f441e3521d514b0d5282d9
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      build-essential cmake git pkg-config ca-certificates \
      libpcre2-dev libssh-dev libssl-dev libcurl4-openssl-dev libpam0g-dev libcrypt-dev zlib1g-dev \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /src
RUN git clone --depth 1 --branch ${LIBYANG_REF} https://github.com/CESNET/libyang.git \
 && cmake -S libyang -B libyang/build -DCMAKE_BUILD_TYPE=Release -DENABLE_TESTS=OFF \
 && cmake --build libyang/build -j"$(nproc)" && cmake --install libyang/build
RUN git clone --depth 1 --branch ${LIBNETCONF2_REF} https://github.com/CESNET/libnetconf2.git \
 && cmake -S libnetconf2 -B libnetconf2/build -DCMAKE_BUILD_TYPE=Release -DENABLE_TESTS=OFF -DENABLE_EXAMPLES=OFF \
 && cmake --build libnetconf2/build -j"$(nproc)" && cmake --install libnetconf2/build
RUN git clone --depth 1 --branch ${SYSREPO_REF} https://github.com/sysrepo/sysrepo.git \
 && cmake -S sysrepo -B sysrepo/build -DCMAKE_BUILD_TYPE=Release -DENABLE_TESTS=OFF -DENABLE_EXAMPLES=OFF \
 && cmake --build sysrepo/build -j"$(nproc)" && cmake --install sysrepo/build
RUN git clone https://github.com/CESNET/netopeer2.git && git -C netopeer2 checkout ${NETOPEER2_REF} \
 && cmake -S netopeer2 -B netopeer2/build -DCMAKE_BUILD_TYPE=Release -DENABLE_TESTS=OFF -DBUILD_CLI=OFF \
      -DSYSREPO_SETUP=OFF -DENABLE_URL=OFF \
 && cmake --build netopeer2/build -j"$(nproc)" && cmake --install netopeer2/build \
 && mkdir -p /opt/netopeer2 && cp -r netopeer2/scripts /opt/netopeer2/scripts

# ---------------------------------------------------------------- NETCONF-enabled VNF (default target)
FROM fw-base AS fw-vnf
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      libpcre2-8-0 libssh-4 libssl3t64 libcurl4t64 libpam0g openssl passwd \
 && rm -rf /var/lib/apt/lists/*
COPY --from=netconf-build /usr/local /usr/local
COPY --from=netconf-build /opt/netopeer2 /opt/netopeer2
COPY --from=netconf-build /etc/pam.d/ /tmp/pam.d/
COPY netconf/yang/ /opt/nexus-agent/yang/
COPY netconf/init-netconf.sh /opt/nexus-agent/init-netconf.sh
RUN cp -n /tmp/pam.d/* /etc/pam.d/ 2>/dev/null || true; rm -rf /tmp/pam.d; sed -i 's/\r$//' /opt/nexus-agent/init-netconf.sh \
 && ldconfig \
 && chmod 0750 /opt/nexus-agent/init-netconf.sh \
 && NP2_MODULE_DIR=/usr/local/share/yang/modules/netopeer2 \
    LN2_MODULE_DIR=/usr/local/share/yang/modules/libnetconf2 \
    NP2_MODULE_PERMS=600 \
    SYSREPOCTL_EXECUTABLE=/usr/local/bin/sysrepoctl \
    bash /opt/netopeer2/scripts/setup.sh \
 && /usr/local/bin/sysrepoctl -i /opt/nexus-agent/yang/nexus-firewall@2026-11-01.yang -p 600 -v2 \
 && rm -rf /dev/shm/sr_* /dev/shm/srsub_* 2>/dev/null || true
LABEL org.opencontainers.image.description="NEXUS NFV firewall VNF (nftables + Netopeer2 NETCONF)"
EXPOSE 830 9443
