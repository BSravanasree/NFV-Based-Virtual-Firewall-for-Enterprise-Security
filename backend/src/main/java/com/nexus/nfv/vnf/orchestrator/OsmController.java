package com.nexus.nfv.vnf.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only views of the OSM catalogue and NS instances (NBI). Lifecycle operations go through the VNF API so
 * they are state-machine controlled and audited.
 */
@RestController
@RequestMapping("/api/v1/osm")
@PreAuthorize(Roles.ANY)
@Tag(name = "OSM Orchestration", description = "Open Source MANO NBI: status, VIMs, packages, NS instances")
public class OsmController {

    private final OsmNbiClient osm;
    private final OsmOrchestrator orchestrator;
    private final OrchestratorRegistry registry;

    public OsmController(OsmNbiClient osm, OsmOrchestrator orchestrator, OrchestratorRegistry registry) {
        this.osm = osm;
        this.orchestrator = orchestrator;
        this.registry = registry;
    }

    @GetMapping("/status")
    @Operation(summary = "Configuration and reachability of the OSM NBI")
    public Map<String, Object> status() {
        var check = orchestrator.check();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", osm.configured());
        m.put("activeOrchestratorMode", registry.mode());
        m.put("endpoint", osm.endpoint());
        m.put("project", osm.config().project());
        m.put("vimAccount", osm.config().vimAccount());
        m.put("nsdId", osm.config().nsdId());
        m.put("reachable", check.up());
        m.put("latencyMs", check.latencyMs());
        m.put("detail", check.detail());
        return m;
    }

    @GetMapping("/vim-accounts")
    public List<Map<String, Object>> vims() {
        return map(osm.get("/admin/v1/vim_accounts"), n -> Map.of(
                "id", n.path("_id").asText(), "name", n.path("name").asText(),
                "type", n.path("vim_type").asText(), "url", n.path("vim_url").asText(),
                "state", n.path("_admin").path("operationalState").asText()));
    }

    @GetMapping("/vnf-packages")
    public List<Map<String, Object>> vnfPackages() {
        return map(osm.get("/vnfpkgm/v1/vnf_packages"), n -> Map.of(
                "id", n.path("_id").asText(), "name", n.path("product-name").asText(n.path("id").asText()),
                "version", n.path("version").asText(), "provider", n.path("provider").asText(),
                "onboardingState", n.path("_admin").path("onboardingState").asText()));
    }

    @GetMapping("/ns-descriptors")
    public List<Map<String, Object>> nsds() {
        return map(osm.get("/nsd/v1/ns_descriptors"), n -> Map.of(
                "id", n.path("_id").asText(), "name", n.path("name").asText(n.path("id").asText()),
                "version", n.path("version").asText(),
                "onboardingState", n.path("_admin").path("onboardingState").asText()));
    }

    @GetMapping("/ns-instances")
    public List<Map<String, Object>> nsInstances() {
        return map(osm.get("/nslcm/v1/ns_instances"), n -> Map.of(
                "id", n.path("_id").asText(), "name", n.path("name").asText(),
                "nsState", n.path("nsState").asText(), "operationalStatus", n.path("operational-status").asText(),
                "configStatus", n.path("config-status").asText(), "detailedStatus", n.path("detailed-status").asText(),
                "created", n.path("create-time").asDouble()));
    }

    @GetMapping("/operations")
    public List<Map<String, Object>> operations() {
        return map(osm.get("/nslcm/v1/ns_lcm_op_occs"), n -> Map.of(
                "id", n.path("_id").asText(), "nsInstanceId", n.path("nsInstanceId").asText(),
                "operation", n.path("lcmOperationType").asText(), "state", n.path("operationState").asText(),
                "startTime", n.path("startTime").asDouble(), "detail", n.path("detailed-status").asText()));
    }

    private static List<Map<String, Object>> map(JsonNode arr, java.util.function.Function<JsonNode, Map<String, Object>> f) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (arr != null && arr.isArray()) arr.forEach(n -> out.add(f.apply(n)));
        return out;
    }
}
