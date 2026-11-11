package com.nexus.nfv.integration;

import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.netconf.NetconfDeviceRepository;
import com.nexus.nfv.sdn.SdnDtos;
import com.nexus.nfv.sdn.SdnService;
import com.nexus.nfv.vnf.VnfEnums;
import com.nexus.nfv.vnf.orchestrator.OrchestratorRegistry;
import com.nexus.nfv.vnf.orchestrator.VnfOrchestrator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Periodically probes every external system and records the result in controller_connections. */
@Service
public class IntegrationHealthService {

    private final ControllerConnectionRepository repo;
    private final OrchestratorRegistry orchestrators;
    private final SdnService sdn;
    private final NetconfDeviceRepository netconf;
    private final NexusProperties props;

    public IntegrationHealthService(ControllerConnectionRepository repo, OrchestratorRegistry orchestrators,
                                    SdnService sdn, NetconfDeviceRepository netconf, NexusProperties props) {
        this.repo = repo;
        this.orchestrators = orchestrators;
        this.sdn = sdn;
        this.netconf = netconf;
        this.props = props;
    }

    public record ConnectionDto(String type, String name, String endpoint, String mode, String status,
                                Long latencyMs, String details, String lastError, Instant lastCheckedAt,
                                Instant lastSuccessAt) {
        static ConnectionDto from(ControllerConnection c) {
            return new ConnectionDto(c.getControllerType(), c.getName(), c.getEndpoint(), c.getMode(), c.getStatus(),
                    c.getLatencyMs(), c.getDetails(), c.getLastError(), c.getLastCheckedAt(), c.getLastSuccessAt());
        }
    }

    @Scheduled(fixedDelayString = "${nexus.monitoring.integration-check-interval-ms:30000}", initialDelay = 5000)
    public void scheduled() {
        checkAll();
    }

    @Transactional
    public List<ConnectionDto> checkAll() {
        String mode = orchestrators.mode();
        VnfOrchestrator docker = orchestrators.forType(VnfEnums.OrchestratorType.DOCKER);
        if ("docker".equals(mode)) {
            save("DOCKER", "nfv-lab", "LAB", docker.check());
        } else {
            disabled("DOCKER", "nfv-lab", props.orchestrator().docker().apiUrl());
        }
        VnfOrchestrator osm = orchestrators.forType(VnfEnums.OrchestratorType.OSM);
        var osmCheck = osm.check();
        if ("osm".equals(mode) || osmCheck.up()) {
            save("OSM", "osm-nbi", "REAL", osmCheck);
        } else {
            ControllerConnection c = upsert("OSM", "osm-nbi");
            c.setEndpoint(props.orchestrator().osm().nbiUrl());
            c.setMode("REAL");
            c.setStatus(props.orchestrator().osm().nbiUrl() == null || props.orchestrator().osm().nbiUrl().isBlank()
                    ? "DISABLED" : "DOWN");
            c.setLastError(osmCheck.detail());
            c.setLatencyMs(null);
            c.setLastCheckedAt(Instant.now());
            repo.save(c);
        }
        SdnDtos.ControllerStatus s = sdn.status();
        ControllerConnection onos = upsert("ONOS", "onos");
        onos.setEndpoint(s.endpoint());
        onos.setMode(s.mode());
        onos.setStatus(s.reachable() ? "UP" : "DOWN");
        onos.setLatencyMs(s.reachable() ? s.latencyMs() : null);
        onos.setDetails(s.reachable() ? "ONOS " + s.version() + " · " + s.devices() + " devices · " + s.hosts()
                + " hosts · " + s.flows() + " flows" + (s.mode().equals("DEMO") ? " (DEMO fixture)" : "") : null);
        onos.setLastError(s.error());
        onos.setLastCheckedAt(Instant.now());
        if (s.reachable()) onos.setLastSuccessAt(onos.getLastCheckedAt());
        repo.save(onos);

        // drop health rows of NETCONF devices that no longer exist
        java.util.Set<String> deviceNames = new java.util.HashSet<>();
        netconf.findAll().forEach(d -> deviceNames.add(d.getName()));
        repo.findAllByOrderByControllerTypeAscNameAsc().stream()
                .filter(c -> "NETCONF".equals(c.getControllerType()) && !deviceNames.contains(c.getName()))
                .forEach(repo::delete);

        netconf.findAll().forEach(d -> {
            ControllerConnection c = upsert("NETCONF", d.getName());
            c.setEndpoint(d.getHost() + ":" + d.getPort());
            c.setMode("REAL");
            c.setStatus(switch (d.getStatus()) {
                case "CONNECTED" -> "UP";
                case "UNKNOWN" -> "UNKNOWN";
                default -> "DOWN";
            });
            c.setDetails("Status from last NETCONF session" + (d.getLastConnectedAt() == null ? "" : " at " + d.getLastConnectedAt()));
            c.setLastError(d.getLastError());
            c.setLastCheckedAt(Instant.now());
            c.setLastSuccessAt(d.getLastConnectedAt());
            repo.save(c);
        });
        return list();
    }

    @Transactional(readOnly = true)
    public List<ConnectionDto> list() {
        return repo.findAllByOrderByControllerTypeAscNameAsc().stream().map(ConnectionDto::from).toList();
    }

    private void save(String type, String name, String mode, VnfOrchestrator.ConnectionCheck check) {
        ControllerConnection c = upsert(type, name);
        c.setEndpoint(check.endpoint());
        c.setMode(mode);
        c.setStatus(check.up() ? "UP" : "DOWN");
        c.setLatencyMs(check.up() ? check.latencyMs() : null);
        c.setDetails(check.up() ? check.detail() : null);
        c.setLastError(check.up() ? null : truncate(check.detail()));
        c.setLastCheckedAt(Instant.now());
        if (check.up()) c.setLastSuccessAt(c.getLastCheckedAt());
        repo.save(c);
    }

    private void disabled(String type, String name, String endpoint) {
        ControllerConnection c = upsert(type, name);
        c.setEndpoint(endpoint);
        c.setMode("DISABLED");
        c.setStatus("DISABLED");
        c.setLastCheckedAt(Instant.now());
        repo.save(c);
    }

    private ControllerConnection upsert(String type, String name) {
        return repo.findByControllerTypeAndName(type, name).orElseGet(() -> {
            ControllerConnection c = new ControllerConnection();
            c.setControllerType(type);
            c.setName(name);
            c.setMode("REAL");
            c.setStatus("UNKNOWN");
            return c;
        });
    }

    private static String truncate(String s) {
        return s == null ? null : (s.length() > 1000 ? s.substring(0, 1000) : s);
    }
}
