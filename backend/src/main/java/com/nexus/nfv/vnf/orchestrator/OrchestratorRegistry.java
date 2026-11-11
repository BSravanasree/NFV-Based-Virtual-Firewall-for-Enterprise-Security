package com.nexus.nfv.vnf.orchestrator;

import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.vnf.VnfEnums;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Resolves the orchestrator for new deployments (configured mode) and for existing VNFs (their recorded type). */
@Component
public class OrchestratorRegistry {

    private final Map<VnfEnums.OrchestratorType, VnfOrchestrator> byType = new EnumMap<>(VnfEnums.OrchestratorType.class);
    private final String mode;

    public OrchestratorRegistry(List<VnfOrchestrator> orchestrators, NexusProperties props) {
        orchestrators.forEach(o -> byType.put(o.type(), o));
        this.mode = props.orchestrator().mode() == null ? "none" : props.orchestrator().mode().toLowerCase();
    }

    public String mode() {
        return mode;
    }

    public VnfOrchestrator defaultOrchestrator() {
        return switch (mode) {
            case "docker" -> byType.get(VnfEnums.OrchestratorType.DOCKER);
            case "osm" -> byType.get(VnfEnums.OrchestratorType.OSM);
            default -> throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ORCHESTRATOR_DISABLED",
                    "No VNF orchestrator is enabled (NEXUS_ORCHESTRATOR_MODE=" + mode + ")");
        };
    }

    public VnfOrchestrator forType(VnfEnums.OrchestratorType type) {
        return byType.get(type);
    }

    public Map<VnfEnums.OrchestratorType, VnfOrchestrator> all() {
        return byType;
    }
}
