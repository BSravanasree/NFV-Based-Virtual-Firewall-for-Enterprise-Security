# Lab endpoint used for the traffic demonstration (client or server role).
FROM ubuntu:24.04
ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      iproute2 iputils-ping curl netcat-openbsd nmap python3 tini ca-certificates dnsutils \
 && rm -rf /var/lib/apt/lists/*
COPY lab-node.sh /usr/local/bin/lab-node
COPY lab_services.py /opt/lab/lab_services.py
RUN sed -i 's/\r$//' /usr/local/bin/lab-node /opt/lab/lab_services.py && chmod 0755 /usr/local/bin/lab-node
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/lab-node"]
