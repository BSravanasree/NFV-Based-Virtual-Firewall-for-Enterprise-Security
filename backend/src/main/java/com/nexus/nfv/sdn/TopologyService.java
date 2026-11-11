package com.nexus.nfv.sdn;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.nfv.common.DataSource;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfRepository;
import com.nexus.nfv.vnf.VnfState;
import com.nexus.nfv.vnf.orchestrator.DockerEngineClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the topology graphs rendered by the UI:
 * <ul>
 *   <li><b>sdn</b> – controller, OpenFlow switches, inter-switch links and hosts as reported by ONOS
 *       (hosts whose IP belongs to a firewall VNF are drawn as firewalls);</li>
 *   <li><b>nfv</b> – the NFV lab segments (management, LAN, DMZ) and attached containers, read from the
 *       Docker Engine network inventory (real lab state).</li>
 * </ul>
 */
@Service
public class TopologyService {

    private final SdnService sdn;
    private final VnfRepository vnfs;
    private final DockerEngineClient docker;
    private final NexusProperties.Docker dockerCfg;

    public TopologyService(SdnService sdn, VnfRepository vnfs, DockerEngineClient docker, NexusProperties props) {
        this.sdn = sdn;
        this.vnfs = vnfs;
        this.docker = docker;
        this.dockerCfg = props.orchestrator().docker();
    }

    public SdnDtos.Topology sdnView() {
        List<SdnDtos.Node> nodes = new ArrayList<>();
        List<SdnDtos.Edge> edges = new ArrayList<>();
        SdnDtos.ControllerStatus st = sdn.status();
        if (!st.reachable()) {
            return new SdnDtos.Topology("sdn", sdn.dataSource(), false,
                    "SDN controller unreachable: " + st.error(), List.of(), List.of());
        }
        Set<String> fwIps = new HashSet<>();
        for (Vnf v : vnfs.findAll()) {
            if (v.getLanIp() != null) fwIps.add(v.getLanIp());
            if (v.getWanIp() != null) fwIps.add(v.getWanIp());
        }
        DataSource ds = sdn.dataSource();
        nodes.add(new SdnDtos.Node("controller", "CONTROLLER", "ONOS", st.version(), "UP", ds));
        for (SdnDtos.DeviceDto d : sdn.devices()) {
            nodes.add(new SdnDtos.Node(d.id(), "SWITCH", d.name() != null ? d.name() : d.id(),
                    d.hwVersion() + " " + d.swVersion() + " · " + d.protocol(), d.available() ? "UP" : "DOWN", ds));
            edges.add(new SdnDtos.Edge("ctl-" + d.id(), "controller", d.id(), "CONTROL", "OpenFlow"));
        }
        Set<String> seen = new HashSet<>();
        for (SdnDtos.LinkDto l : sdn.links()) {
            String key = l.srcDevice().compareTo(l.dstDevice()) < 0
                    ? l.srcDevice() + l.srcPort() + l.dstDevice() + l.dstPort()
                    : l.dstDevice() + l.dstPort() + l.srcDevice() + l.srcPort();
            if (!seen.add(key)) continue; // ONOS reports both directions
            edges.add(new SdnDtos.Edge("lnk-" + key, l.srcDevice(), l.dstDevice(), "LINK",
                    l.srcPort() + "↔" + l.dstPort()));
        }
        for (SdnDtos.HostDto h : sdn.hosts()) {
            boolean firewall = h.ipAddresses().stream().anyMatch(fwIps::contains)
                    || (h.name() != null && h.name().toLowerCase().contains("fw"));
            String label = h.name() != null ? h.name() : String.join(",", h.ipAddresses());
            nodes.add(new SdnDtos.Node(h.id(), firewall ? "FIREWALL" : "HOST", label,
                    String.join(", ", h.ipAddresses()) + " · " + h.mac(), "UP", ds));
            if (h.deviceId() != null) {
                edges.add(new SdnDtos.Edge("acc-" + h.id(), h.id(), h.deviceId(), "ACCESS", "port " + h.port()));
            }
        }
        return new SdnDtos.Topology("sdn", ds, true, null, nodes, edges);
    }

    public SdnDtos.Topology nfvView() {
        List<SdnDtos.Node> nodes = new ArrayList<>();
        List<SdnDtos.Edge> edges = new ArrayList<>();
        Map<String, String> segments = Map.of(
                dockerCfg.lanNetwork(), "LAN " + dockerCfg.lanCidr(),
                dockerCfg.wanNetwork(), "DMZ " + dockerCfg.wanCidr(),
                dockerCfg.mgmtNetwork(), "Management");
        try {
            Set<String> containers = new HashSet<>();
            for (var seg : segments.entrySet()) {
                var net = docker.network(seg.getKey());
                if (net.isEmpty()) continue;
                String segId = "seg-" + seg.getKey();
                nodes.add(new SdnDtos.Node(segId, "SEGMENT", seg.getValue(), seg.getKey(), "UP", DataSource.LAB));
                Iterator<Map.Entry<String, JsonNode>> it = net.get().path("Containers").fields();
                while (it.hasNext()) {
                    JsonNode c = it.next().getValue();
                    String name = c.path("Name").asText();
                    String ip = c.path("IPv4Address").asText();
                    String id = "ctr-" + name;
                    if (containers.add(name)) {
                        nodes.add(new SdnDtos.Node(id, classify(name), name, "", "UP", DataSource.LAB));
                    }
                    edges.add(new SdnDtos.Edge(id + "-" + segId, id, segId, "ATTACHMENT", ip));
                }
            }
            if (nodes.isEmpty()) {
                return new SdnDtos.Topology("nfv", DataSource.LAB, false,
                        "Lab networks not found. Start the lab: docker compose --profile lab up -d", List.of(), List.of());
            }
            // annotate firewall nodes with VNF state from the platform
            for (Vnf v : vnfs.findAll()) {
                String id = "ctr-nexus-vnf-" + v.getName();
                for (int i = 0; i < nodes.size(); i++) {
                    SdnDtos.Node n = nodes.get(i);
                    if (n.id().equals(id)) {
                        nodes.set(i, new SdnDtos.Node(n.id(), "FIREWALL", v.getName(),
                                "VNF-" + String.format("%05d", v.getId()) + " · rules r" + v.getAppliedRevision(),
                                v.getState() == VnfState.ACTIVE ? "UP" : v.getState().name(), DataSource.LAB));
                    }
                }
            }
            return new SdnDtos.Topology("nfv", DataSource.LAB, true, null, nodes, edges);
        } catch (RuntimeException ex) {
            return new SdnDtos.Topology("nfv", DataSource.LAB, false, "Docker Engine unavailable: " + ex.getMessage(),
                    List.of(), List.of());
        }
    }

    static String classify(String name) {
        String n = name.toLowerCase();
        if (n.startsWith("nexus-vnf-")) return "FIREWALL";
        if (n.contains("client")) return "CLIENT";
        if (n.contains("server")) return "SERVER";
        if (n.contains("backend")) return "CONTROLLER";
        return "HOST";
    }
}
