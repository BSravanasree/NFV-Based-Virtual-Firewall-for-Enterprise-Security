package com.nexus.nfv.vnf.orchestrator;

import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Port (hexagonal architecture) for VNF lifecycle management. Implementations must only report success
 * when the underlying infrastructure has confirmed the operation; any failure is raised as an
 * {@link com.nexus.nfv.common.exception.InfrastructureException}.
 */
public interface VnfOrchestrator {

    VnfEnums.OrchestratorType type();

    /** Instantiate the VNF and block until the infrastructure confirms it is running (or fails). */
    Deployment instantiate(Vnf vnf, DeployOptions options);

    void start(Vnf vnf);

    void stop(Vnf vnf);

    void restart(Vnf vnf);

    /** Change compute allocation (vertical) or instance count (horizontal, OSM scaling groups). */
    void scale(Vnf vnf, BigDecimal cpus, int memoryMb, ScaleDirection direction);

    void terminate(Vnf vnf);

    /** Observed runtime status; empty when the resource no longer exists. */
    Optional<RuntimeStatus> inspect(Vnf vnf);

    /** Resource usage sample; empty if the infrastructure cannot provide one. */
    Optional<ResourceUsage> usage(Vnf vnf);

    ConnectionCheck check();

    record DeployOptions(String netconfPassword) {
    }

    record Deployment(String externalId, String mgmtIp, String lanIp, String wanIp, String agentUrl,
                      String netconfHost, Integer netconfPort, String message) {
    }

    record RuntimeStatus(boolean running, String rawState, String detail) {
    }

    record ResourceUsage(BigDecimal cpuPercent, BigDecimal memoryPercent, BigDecimal memoryUsageMb,
                         long rxBytes, long txBytes) {
    }

    record ConnectionCheck(boolean up, String endpoint, long latencyMs, String detail) {
    }

    enum ScaleDirection { VERTICAL, SCALE_OUT, SCALE_IN }
}
