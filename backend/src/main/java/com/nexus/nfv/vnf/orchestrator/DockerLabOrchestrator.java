package com.nexus.nfv.vnf.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.common.exception.InfrastructureException;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.firewall.agent.FirewallAgentClient;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * NFV lab orchestrator: instantiates the Ubuntu/nftables firewall VNF image as a container attached to
 * three isolated networks (management, LAN, DMZ/WAN) through the Docker Engine API. Every lifecycle step
 * is confirmed against the engine and the in-VNF agent before success is reported.
 */
@Component
public class DockerLabOrchestrator implements VnfOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DockerLabOrchestrator.class);
    static final String LABEL_MANAGED = "nexus.vnf";
    static final String LABEL_VNF_ID = "nexus.vnf.id";
    private static final long AGENT_READY_TIMEOUT_MS = 60_000;

    private final DockerEngineClient docker;
    private final FirewallAgentClient agent;
    private final NexusProperties.Docker cfg;
    private final String agentToken;

    public DockerLabOrchestrator(DockerEngineClient docker, FirewallAgentClient agent, NexusProperties props) {
        this.docker = docker;
        this.agent = agent;
        this.cfg = props.orchestrator().docker();
        this.agentToken = props.agent().token();
    }

    @Override
    public VnfEnums.OrchestratorType type() {
        return VnfEnums.OrchestratorType.DOCKER;
    }

    static String containerName(Vnf vnf) {
        return "nexus-vnf-" + vnf.getName();
    }

    @Override
    public Deployment instantiate(Vnf vnf, DeployOptions options) {
        if (agentToken == null || agentToken.isBlank()) {
            throw InfrastructureException.notConfigured("Firewall agent", "NEXUS_AGENT_TOKEN is not set");
        }
        if (!docker.imageExists(cfg.vnfImage())) {
            throw new InfrastructureException(DockerEngineClient.SYSTEM, "VNF image '" + cfg.vnfImage()
                    + "' is not present. Build it with: docker compose build fw-vnf-image");
        }
        for (String net : List.of(cfg.mgmtNetwork(), cfg.lanNetwork(), cfg.wanNetwork())) {
            if (docker.network(net).isEmpty()) {
                throw new InfrastructureException(DockerEngineClient.SYSTEM, "lab network '" + net
                        + "' does not exist. Start the lab with: docker compose --profile lab up -d");
            }
        }
        String name = containerName(vnf);
        docker.inspect(name).ifPresent(existing -> {
            if (!String.valueOf(vnf.getId()).equals(existing.path("Config").path("Labels").path(LABEL_VNF_ID).asText())) {
                throw new InfrastructureException(DockerEngineClient.SYSTEM, "a container named '" + name
                        + "' exists and is not managed by this VNF; refusing to touch it");
            }
            log.info("Removing stale container {} from a previous failed deployment", name);
            docker.remove(existing.path("Id").asText());
        });

        List<String> env = new ArrayList<>();
        env.add("NEXUS_AGENT_TOKEN=" + agentToken);
        env.add("NEXUS_LAN_CIDR=" + cfg.lanCidr());
        env.add("NEXUS_WAN_CIDR=" + cfg.wanCidr());
        env.add("NEXUS_VNF_NAME=" + vnf.getName());
        env.add("NETCONF_ENABLED=" + (cfg.netconfEnabled() && options.netconfPassword() != null));
        if (options.netconfPassword() != null) env.add("NETCONF_PASSWORD=" + options.netconfPassword());

        Map<String, Object> hostConfig = new LinkedHashMap<>();
        hostConfig.put("CapAdd", List.of("NET_ADMIN", "NET_RAW"));
        hostConfig.put("CapDrop", List.of("SYS_ADMIN", "SYS_MODULE", "SYS_PTRACE"));
        hostConfig.put("SecurityOpt", List.of("no-new-privileges:true"));
        hostConfig.put("NanoCpus", toNanoCpus(vnf.getCpuLimit()));
        hostConfig.put("Memory", (long) vnf.getMemoryLimitMb() * 1024 * 1024);
        hostConfig.put("PidsLimit", 512);
        hostConfig.put("NetworkMode", cfg.mgmtNetwork());
        hostConfig.put("Sysctls", Map.of("net.ipv4.ip_forward", "1",
                "net.ipv4.conf.all.rp_filter", "0", "net.ipv4.conf.default.rp_filter", "0"));
        hostConfig.put("RestartPolicy", Map.of("Name", "no"));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("Image", cfg.vnfImage());
        spec.put("Hostname", vnf.getName());
        spec.put("Env", env);
        spec.put("Labels", Map.of(LABEL_MANAGED, "true", LABEL_VNF_ID, String.valueOf(vnf.getId()),
                "nexus.vnf.name", vnf.getName(), "nexus.vnf.type", vnf.getVnfType().name()));
        spec.put("HostConfig", hostConfig);
        spec.put("NetworkingConfig", Map.of("EndpointsConfig", Map.of(cfg.mgmtNetwork(), Map.of())));

        String id = docker.createContainer(name, spec);
        try {
            docker.connectNetwork(cfg.lanNetwork(), id, vnf.getLanIp());
            docker.connectNetwork(cfg.wanNetwork(), id, vnf.getWanIp());
            docker.start(id);
            String agentUrl = "http://" + name + ":" + cfg.agentPort();
            waitForAgent(agentUrl);
            JsonNode info = docker.inspect(id).orElseThrow(() ->
                    new InfrastructureException(DockerEngineClient.SYSTEM, "container disappeared after start"));
            JsonNode nets = info.path("NetworkSettings").path("Networks");
            String mgmtIp = nets.path(cfg.mgmtNetwork()).path("IPAddress").asText(null);
            String lanIp = nets.path(cfg.lanNetwork()).path("IPAddress").asText(vnf.getLanIp());
            String wanIp = nets.path(cfg.wanNetwork()).path("IPAddress").asText(vnf.getWanIp());
            return new Deployment(id, mgmtIp, lanIp, wanIp, agentUrl, name, 830,
                    "Container " + id.substring(0, 12) + " running; agent healthy");
        } catch (RuntimeException ex) {
            log.warn("Deployment of {} failed, rolling back container {}: {}", name, id, ex.getMessage());
            try {
                docker.remove(id);
            } catch (RuntimeException cleanup) {
                log.error("Rollback of container {} failed", id, cleanup);
            }
            throw ex;
        }
    }

    @Override
    public void start(Vnf vnf) {
        String id = managedId(vnf);
        docker.start(id);
        waitForAgent(vnf.getAgentUrl());
    }

    @Override
    public void stop(Vnf vnf) {
        String id = managedId(vnf);
        docker.stop(id, 10);
        boolean running = docker.inspect(id).map(n -> n.path("State").path("Running").asBoolean()).orElse(false);
        if (running) {
            throw new InfrastructureException(DockerEngineClient.SYSTEM, "container is still running after stop");
        }
    }

    @Override
    public void restart(Vnf vnf) {
        String id = managedId(vnf);
        docker.restart(id, 10);
        waitForAgent(vnf.getAgentUrl());
    }

    @Override
    public void scale(Vnf vnf, BigDecimal cpus, int memoryMb, ScaleDirection direction) {
        if (direction != ScaleDirection.VERTICAL) {
            throw ApiException.badRequest("Horizontal scaling (" + direction + ") requires the OSM orchestrator "
                    + "with a VNFD scaling-group; the Docker lab supports vertical scaling only");
        }
        docker.update(managedId(vnf), toNanoCpus(cpus), (long) memoryMb * 1024 * 1024);
    }

    @Override
    public void terminate(Vnf vnf) {
        if (vnf.getExternalId() == null) return;
        Optional<JsonNode> info = docker.inspect(vnf.getExternalId());
        if (info.isEmpty()) return; // already gone – terminate is idempotent
        ensureManaged(vnf, info.get());
        docker.remove(vnf.getExternalId());
        if (docker.inspect(vnf.getExternalId()).isPresent()) {
            throw new InfrastructureException(DockerEngineClient.SYSTEM, "container still exists after removal");
        }
    }

    @Override
    public Optional<RuntimeStatus> inspect(Vnf vnf) {
        if (vnf.getExternalId() == null) return Optional.empty();
        return docker.inspect(vnf.getExternalId()).map(n -> {
            JsonNode s = n.path("State");
            return new RuntimeStatus(s.path("Running").asBoolean(), s.path("Status").asText(),
                    s.path("Error").asText(""));
        });
    }

    @Override
    public Optional<ResourceUsage> usage(Vnf vnf) {
        if (vnf.getExternalId() == null) return Optional.empty();
        JsonNode st = docker.stats(vnf.getExternalId());
        return Optional.of(parseStats(st));
    }

    /** CPU% and memory computation identical to the `docker stats` CLI. */
    static ResourceUsage parseStats(JsonNode st) {
        JsonNode cpu = st.path("cpu_stats");
        JsonNode pre = st.path("precpu_stats");
        double cpuDelta = cpu.path("cpu_usage").path("total_usage").asDouble() - pre.path("cpu_usage").path("total_usage").asDouble();
        double sysDelta = cpu.path("system_cpu_usage").asDouble() - pre.path("system_cpu_usage").asDouble();
        int online = cpu.path("online_cpus").asInt(0);
        if (online == 0) online = Math.max(1, cpu.path("cpu_usage").path("percpu_usage").size());
        double cpuPct = (sysDelta > 0 && cpuDelta > 0) ? (cpuDelta / sysDelta) * online * 100.0 : 0.0;

        JsonNode mem = st.path("memory_stats");
        double usage = mem.path("usage").asDouble();
        double cache = mem.path("stats").path("inactive_file").asDouble(mem.path("stats").path("total_inactive_file").asDouble());
        double used = Math.max(0, usage - cache);
        double limit = mem.path("limit").asDouble();
        double memPct = limit > 0 ? used / limit * 100.0 : 0.0;

        long rx = 0, tx = 0;
        Iterator<JsonNode> it = st.path("networks").elements();
        while (it.hasNext()) {
            JsonNode n = it.next();
            rx += n.path("rx_bytes").asLong();
            tx += n.path("tx_bytes").asLong();
        }
        return new ResourceUsage(bd(cpuPct), bd(memPct), bd(used / (1024 * 1024)), rx, tx);
    }

    @Override
    public ConnectionCheck check() {
        long t0 = System.nanoTime();
        try {
            JsonNode v = docker.version();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            return new ConnectionCheck(true, docker.endpoint(), ms,
                    "Docker Engine " + v.path("Version").asText() + " (API " + v.path("ApiVersion").asText() + ")");
        } catch (RuntimeException ex) {
            return new ConnectionCheck(false, docker.endpoint(), -1, ex.getMessage());
        }
    }

    private void waitForAgent(String agentUrl) {
        long deadline = System.currentTimeMillis() + AGENT_READY_TIMEOUT_MS;
        RuntimeException last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                var h = agent.health(agentUrl);
                if ("ok".equalsIgnoreCase(h.status())) return;
                last = new InfrastructureException("Firewall agent", "status=" + h.status());
            } catch (RuntimeException ex) {
                last = ex;
            }
            sleep(1500);
        }
        throw new InfrastructureException("Firewall agent", "did not become healthy within "
                + AGENT_READY_TIMEOUT_MS / 1000 + "s" + (last == null ? "" : ": " + last.getMessage()));
    }

    private String managedId(Vnf vnf) {
        if (vnf.getExternalId() == null) {
            throw ApiException.invalidState("VNF has no deployed container");
        }
        JsonNode info = docker.inspect(vnf.getExternalId()).orElseThrow(() -> new InfrastructureException(
                DockerEngineClient.SYSTEM, "container " + vnf.getExternalId().substring(0, 12) + " no longer exists"));
        ensureManaged(vnf, info);
        return vnf.getExternalId();
    }

    private static void ensureManaged(Vnf vnf, JsonNode info) {
        JsonNode labels = info.path("Config").path("Labels");
        if (!"true".equals(labels.path(LABEL_MANAGED).asText())
                || !String.valueOf(vnf.getId()).equals(labels.path(LABEL_VNF_ID).asText())) {
            throw new InfrastructureException(DockerEngineClient.SYSTEM,
                    "container is not labelled as managed by this VNF; refusing operation");
        }
    }

    private static long toNanoCpus(BigDecimal cpus) {
        return cpus.multiply(BigDecimal.valueOf(1_000_000_000L)).longValue();
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfrastructureException("Firewall agent", "interrupted while waiting for agent");
        }
    }
}
