package com.nexus.nfv.sdn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.DataSource;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.common.net.Ipv4;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.sdn.onos.OnosGateway;
import com.nexus.nfv.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SDN integration (ONOS northbound REST): inventory, flows and security policy compilation to OpenFlow.
 * Inventory responses are persisted (network_devices / network_hosts / topology_links) with their data source.
 */
@Service
public class SdnService {

    private final OnosGateway onos;
    private final ObjectMapper mapper;
    private final NetworkDeviceRepository devices;
    private final NetworkHostRepository hosts;
    private final TopologyLinkRepository links;
    private final NetworkPolicyRepository policies;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final String appId;
    private final String mode;

    public SdnService(OnosGateway onos, ObjectMapper mapper, NetworkDeviceRepository devices,
                      NetworkHostRepository hosts, TopologyLinkRepository links, NetworkPolicyRepository policies,
                      AuditService audit, PlatformTransactionManager txm, NexusProperties props) {
        this.onos = onos;
        this.mapper = mapper;
        this.devices = devices;
        this.hosts = hosts;
        this.links = links;
        this.policies = policies;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
        this.appId = props.onos().appId();
        this.mode = props.onos().demo() ? "DEMO" : "REAL";
    }

    public OnosGateway gateway() {
        return onos;
    }

    // ------------------------------------------------------------------ controller

    public SdnDtos.ControllerStatus status() {
        long t0 = System.nanoTime();
        try {
            JsonNode info = onos.controllerInfo();
            long latency = (System.nanoTime() - t0) / 1_000_000;
            int d = onos.devices().size();
            int h = onos.hosts().size();
            int l = onos.links().size();
            int f = onos.flows(null).size();
            List<String> apps = new ArrayList<>();
            onos.applications().forEach(a -> {
                if ("ACTIVE".equals(a.path("state").asText())) apps.add(a.path("name").asText());
            });
            return new SdnDtos.ControllerStatus(mode, onos.dataSource(), onos.endpoint(), true, latency,
                    info.path("version").asText(null), info.path("id").asText(null), d, h, l, f, apps, null);
        } catch (RuntimeException ex) {
            return new SdnDtos.ControllerStatus(mode, onos.dataSource(), onos.endpoint(), false, -1, null, null,
                    0, 0, 0, 0, List.of(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ inventory

    public List<SdnDtos.DeviceDto> devices() {
        List<SdnDtos.DeviceDto> out = new ArrayList<>();
        for (JsonNode d : onos.devices()) {
            JsonNode a = d.path("annotations");
            out.add(new SdnDtos.DeviceDto(d.path("id").asText(), a.path("name").asText(null), d.path("type").asText(),
                    d.path("available").asBoolean(), d.path("mfr").asText(null), d.path("hw").asText(null),
                    d.path("sw").asText(null), a.path("protocol").asText(null),
                    a.path("managementAddress").asText(null), a.path("channelId").asText(null),
                    d.path("role").asText(null), onos.dataSource()));
        }
        persistDevices(out);
        return out;
    }

    public List<SdnDtos.PortDto> ports(String deviceId) {
        requireDeviceId(deviceId);
        Map<String, JsonNode> stats = new HashMap<>();
        try {
            for (JsonNode s : onos.portStatistics(deviceId).path("statistics")) {
                for (JsonNode p : s.path("ports")) stats.put(p.path("port").asText(), p);
            }
        } catch (RuntimeException ignored) {
            // statistics are optional (not all drivers provide them)
        }
        List<SdnDtos.PortDto> out = new ArrayList<>();
        for (JsonNode p : onos.ports(deviceId)) {
            JsonNode a = p.path("annotations");
            JsonNode s = stats.get(p.path("port").asText());
            out.add(new SdnDtos.PortDto(p.path("port").asText(), a.path("portName").asText(null),
                    p.path("isEnabled").asBoolean(), p.path("type").asText(null), p.path("portSpeed").asLong(),
                    a.path("portMac").asText(null),
                    s == null ? null : s.path("packetsReceived").asLong(), s == null ? null : s.path("packetsSent").asLong(),
                    s == null ? null : s.path("bytesReceived").asLong(), s == null ? null : s.path("bytesSent").asLong(),
                    s == null ? null : s.path("packetsRxDropped").asLong(), s == null ? null : s.path("packetsTxDropped").asLong()));
        }
        return out;
    }

    public List<SdnDtos.HostDto> hosts() {
        List<SdnDtos.HostDto> out = new ArrayList<>();
        for (JsonNode h : onos.hosts()) {
            List<String> ips = new ArrayList<>();
            h.path("ipAddresses").forEach(i -> ips.add(i.asText()));
            JsonNode loc = h.path("locations").path(0);
            if (loc.isMissingNode()) loc = h.path("location");
            out.add(new SdnDtos.HostDto(h.path("id").asText(), h.path("annotations").path("name").asText(null),
                    h.path("mac").asText(), h.path("vlan").asText(null), ips, loc.path("elementId").asText(null),
                    loc.path("port").asText(null), onos.dataSource()));
        }
        persistHosts(out);
        return out;
    }

    public List<SdnDtos.LinkDto> links() {
        List<SdnDtos.LinkDto> out = new ArrayList<>();
        for (JsonNode l : onos.links()) {
            out.add(new SdnDtos.LinkDto(l.path("src").path("device").asText(), l.path("src").path("port").asText(),
                    l.path("dst").path("device").asText(), l.path("dst").path("port").asText(),
                    l.path("type").asText(null), l.path("state").asText(null), onos.dataSource()));
        }
        persistLinks(out);
        return out;
    }

    public List<SdnDtos.FlowDto> flows(String deviceId) {
        if (deviceId != null) requireDeviceId(deviceId);
        List<SdnDtos.FlowDto> out = new ArrayList<>();
        for (JsonNode f : onos.flows(deviceId)) {
            out.add(new SdnDtos.FlowDto(f.path("id").asText(), f.path("deviceId").asText(), f.path("appId").asText(),
                    f.path("priority").asInt(), f.path("tableId").asInt(), f.path("state").asText(),
                    f.path("packets").asLong(), f.path("bytes").asLong(), f.path("life").asLong(),
                    f.path("isPermanent").asBoolean(), describeMatch(f.path("selector")),
                    describeActions(f.path("treatment")), appId.equals(f.path("appId").asText()), onos.dataSource()));
        }
        return out;
    }

    public void deleteFlow(String deviceId, String flowId) {
        requireDeviceId(deviceId);
        if (!flowId.matches("^-?\\d{1,20}$")) throw ApiException.badRequest("Invalid flow id");
        try {
            onos.removeFlow(deviceId, flowId);
            audit.success(AuditAction.FLOW_DELETED, "SDN", "FLOW", deviceId + "/" + flowId,
                    "Removed flow " + flowId + " from " + deviceId + " [" + onos.dataSource() + "]");
        } catch (RuntimeException ex) {
            audit.record(AuditAction.FLOW_DELETED, "SDN", "FLOW", deviceId + "/" + flowId, false, ex.getMessage());
            throw ex;
        }
    }

    // ------------------------------------------------------------------ policies

    @Transactional(readOnly = true)
    public List<SdnDtos.PolicyDto> policies() {
        return policies.findAllByOrderByPriorityDesc().stream().map(SdnDtos.PolicyDto::from).toList();
    }

    public SdnDtos.PolicyDto createPolicy(SdnDtos.PolicyRequest req) {
        if (policies.existsByName(req.name().trim())) throw ApiException.conflict("Policy name already exists");
        NetworkPolicy p = new NetworkPolicy();
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setDeviceId(req.deviceId());
        p.setPriority(req.priority());
        p.setIpProtocol(req.ipProtocol());
        p.setSourceCidr(Ipv4.normalizeCidr(req.sourceCidr(), "sourceCidr"));
        p.setDestinationCidr(Ipv4.normalizeCidr(req.destinationCidr(), "destinationCidr"));
        p.setDestinationPort(req.destinationPort());
        p.setAction(req.action());
        p.setOutputPort(req.outputPort() == null || req.outputPort().isBlank() ? null : req.outputPort().trim());
        p.setStatus(NetworkPolicy.Status.PENDING);
        p.setDataSource(onos.dataSource());
        p.setCreatedBy(CurrentUser.username());
        var flow = FlowTranslator.toOnosFlow(p, mapper); // validates before anything is persisted
        tx.executeWithoutResult(s -> policies.save(p));

        try {
            String flowId = onos.installFlow(p.getDeviceId(), flow, appId);
            p.setOnosFlowId(flowId);
            p.setStatus(NetworkPolicy.Status.INSTALLED);
            p.setLastError(null);
            audit.success(AuditAction.NET_POLICY_CREATED, "SDN", "NETWORK_POLICY", p.getId(),
                    "Policy '" + p.getName() + "' installed as flow " + flowId + " on " + p.getDeviceId()
                            + " [" + onos.dataSource() + "]");
        } catch (RuntimeException ex) {
            p.setStatus(NetworkPolicy.Status.FAILED);
            p.setLastError(ex.getMessage() == null ? "unknown" : ex.getMessage().substring(0, Math.min(1000, ex.getMessage().length())));
            audit.record(AuditAction.NET_POLICY_CREATED, "SDN", "NETWORK_POLICY", p.getId(), false,
                    "Policy '" + p.getName() + "' NOT installed: " + ex.getMessage());
        }
        tx.executeWithoutResult(s -> policies.save(p));
        return SdnDtos.PolicyDto.from(p);
    }

    public SdnDtos.PolicyDto reinstall(Long id) {
        NetworkPolicy p = policies.findById(id).orElseThrow(() -> ApiException.notFound("Network policy", id));
        if (p.getStatus() == NetworkPolicy.Status.INSTALLED) return SdnDtos.PolicyDto.from(p);
        try {
            p.setOnosFlowId(onos.installFlow(p.getDeviceId(), FlowTranslator.toOnosFlow(p, mapper), appId));
            p.setStatus(NetworkPolicy.Status.INSTALLED);
            p.setDataSource(onos.dataSource());
            p.setLastError(null);
        } catch (RuntimeException ex) {
            p.setStatus(NetworkPolicy.Status.FAILED);
            p.setLastError(ex.getMessage());
        }
        tx.executeWithoutResult(s -> policies.save(p));
        return SdnDtos.PolicyDto.from(p);
    }

    public void deletePolicy(Long id) {
        NetworkPolicy p = policies.findById(id).orElseThrow(() -> ApiException.notFound("Network policy", id));
        if (p.getStatus() == NetworkPolicy.Status.INSTALLED && p.getOnosFlowId() != null) {
            if (p.getDataSource() != onos.dataSource()) {
                throw ApiException.invalidState("Policy was installed in " + p.getDataSource()
                        + " mode but the controller is now in " + onos.dataSource() + " mode");
            }
            onos.removeFlow(p.getDeviceId(), p.getOnosFlowId()); // failure propagates: record kept, flow still there
        }
        tx.executeWithoutResult(s -> policies.deleteById(id));
        audit.success(AuditAction.NET_POLICY_DELETED, "SDN", "NETWORK_POLICY", id,
                "Policy '" + p.getName() + "' removed (flow " + p.getOnosFlowId() + ")");
    }

    // ------------------------------------------------------------------ helpers

    static String describeMatch(JsonNode selector) {
        List<String> parts = new ArrayList<>();
        for (JsonNode c : selector.path("criteria")) {
            String type = c.path("type").asText();
            String v = switch (type) {
                case "ETH_TYPE" -> c.path("ethType").asText();
                case "IN_PORT" -> c.path("port").asText();
                case "IPV4_SRC", "IPV4_DST" -> c.path("ip").asText();
                case "IP_PROTO" -> c.path("protocol").asText();
                case "TCP_SRC", "TCP_DST" -> c.path("tcpPort").asText();
                case "UDP_SRC", "UDP_DST" -> c.path("udpPort").asText();
                case "ETH_SRC", "ETH_DST" -> c.path("mac").asText();
                default -> c.toString();
            };
            parts.add(type.toLowerCase() + "=" + v);
        }
        return parts.isEmpty() ? "*" : String.join(", ", parts);
    }

    static String describeActions(JsonNode treatment) {
        List<String> parts = new ArrayList<>();
        for (JsonNode i : treatment.path("instructions")) {
            String type = i.path("type").asText();
            parts.add("OUTPUT".equals(type) ? "output:" + i.path("port").asText() : type.toLowerCase());
        }
        return parts.isEmpty() ? "drop" : String.join(", ", parts);
    }

    private static void requireDeviceId(String deviceId) {
        if (deviceId == null || !deviceId.matches("^[a-z]+:[0-9a-zA-Z:._-]{1,64}$")) {
            throw ApiException.badRequest("Invalid device id");
        }
    }

    private void persistDevices(List<SdnDtos.DeviceDto> list) {
        Instant now = Instant.now();
        tx.executeWithoutResult(s -> list.forEach(d -> {
            NetworkDevice e = devices.findByDeviceIdAndDataSource(d.id(), d.dataSource()).orElseGet(NetworkDevice::new);
            e.setDeviceId(d.id());
            e.setDeviceType(d.type());
            e.setManufacturer(d.manufacturer());
            e.setHwVersion(d.hwVersion());
            e.setSwVersion(d.swVersion());
            e.setProtocol(d.protocol());
            e.setManagementAddress(d.managementAddress());
            e.setAvailable(d.available());
            e.setDataSource(d.dataSource());
            e.setLastSeen(now);
            devices.save(e);
        }));
    }

    private void persistHosts(List<SdnDtos.HostDto> list) {
        Instant now = Instant.now();
        tx.executeWithoutResult(s -> list.forEach(h -> {
            NetworkHost e = hosts.findByHostIdAndDataSource(h.id(), h.dataSource()).orElseGet(NetworkHost::new);
            e.setHostId(h.id());
            e.setMac(h.mac());
            e.setVlan(h.vlan());
            e.setIpAddresses(String.join(",", h.ipAddresses()));
            e.setLocationDevice(h.deviceId());
            e.setLocationPort(h.port());
            e.setDataSource(h.dataSource());
            e.setLastSeen(now);
            hosts.save(e);
        }));
    }

    private void persistLinks(List<SdnDtos.LinkDto> list) {
        Instant now = Instant.now();
        tx.executeWithoutResult(s -> list.forEach(l -> {
            TopologyLink e = links.findBySrcDeviceAndSrcPortAndDstDeviceAndDstPortAndDataSource(
                    l.srcDevice(), l.srcPort(), l.dstDevice(), l.dstPort(), l.dataSource()).orElseGet(TopologyLink::new);
            e.setSrcDevice(l.srcDevice());
            e.setSrcPort(l.srcPort());
            e.setDstDevice(l.dstDevice());
            e.setDstPort(l.dstPort());
            e.setLinkType(l.type());
            e.setState(l.state());
            e.setDataSource(l.dataSource());
            e.setLastSeen(now);
            links.save(e);
        }));
    }

    public DataSource dataSource() {
        return onos.dataSource();
    }
}
