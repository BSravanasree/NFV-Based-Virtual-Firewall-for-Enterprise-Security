package com.nexus.nfv.integration;

import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integrations", description = "Health of OSM, ONOS, Docker lab and NETCONF endpoints")
public class IntegrationController {

    private final IntegrationHealthService service;
    private final NexusProperties props;

    public IntegrationController(IntegrationHealthService service, NexusProperties props) {
        this.service = service;
        this.props = props;
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    public List<IntegrationHealthService.ConnectionDto> list() {
        return service.list();
    }

    @PostMapping("/check")
    @PreAuthorize(Roles.OPERATE)
    @Operation(summary = "Probe all integrations now")
    public List<IntegrationHealthService.ConnectionDto> check() {
        return service.checkAll();
    }

    /** Non-secret runtime configuration (secrets are reported only as configured / not configured). */
    @GetMapping("/config")
    @PreAuthorize(Roles.ANY)
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orchestratorMode", props.orchestrator().mode());
        m.put("vnfImage", props.orchestrator().docker().vnfImage());
        m.put("lanNetwork", props.orchestrator().docker().lanNetwork() + " " + props.orchestrator().docker().lanCidr());
        m.put("wanNetwork", props.orchestrator().docker().wanNetwork() + " " + props.orchestrator().docker().wanCidr());
        m.put("mgmtNetwork", props.orchestrator().docker().mgmtNetwork());
        m.put("netconfInVnf", props.orchestrator().docker().netconfEnabled());
        m.put("onosMode", props.onos().demo() ? "DEMO" : "REAL");
        m.put("onosUrl", props.onos().url());
        m.put("osmNbiUrl", props.orchestrator().osm().nbiUrl());
        m.put("agentTokenConfigured", notBlank(props.agent().token()));
        m.put("jwtSecretConfigured", notBlank(props.security().jwtSecret()));
        m.put("credentialKeyConfigured", notBlank(props.security().credentialKey()));
        m.put("osmPasswordConfigured", notBlank(props.orchestrator().osm().password()));
        m.put("onosPasswordConfigured", notBlank(props.onos().password()));
        m.put("pollIntervalMs", props.monitoring().pollIntervalMs());
        m.put("jwtTtlMinutes", props.security().jwtTtlMinutes());
        return m;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
