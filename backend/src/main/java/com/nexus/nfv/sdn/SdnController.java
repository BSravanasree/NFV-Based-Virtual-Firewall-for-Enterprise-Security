package com.nexus.nfv.sdn;

import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sdn")
@Tag(name = "SDN / ONOS / OpenFlow", description = "Controller status, inventory, flow rules and network security policies")
public class SdnController {

    private final SdnService sdn;
    private final TopologyService topology;

    public SdnController(SdnService sdn, TopologyService topology) {
        this.sdn = sdn;
        this.topology = topology;
    }

    @GetMapping("/status")
    @PreAuthorize(Roles.ANY)
    public SdnDtos.ControllerStatus status() {
        return sdn.status();
    }

    @GetMapping("/devices")
    @PreAuthorize(Roles.ANY)
    public List<SdnDtos.DeviceDto> devices() {
        return sdn.devices();
    }

    @GetMapping("/devices/{deviceId}/ports")
    @PreAuthorize(Roles.ANY)
    public List<SdnDtos.PortDto> ports(@PathVariable String deviceId) {
        return sdn.ports(deviceId);
    }

    @GetMapping("/hosts")
    @PreAuthorize(Roles.ANY)
    public List<SdnDtos.HostDto> hosts() {
        return sdn.hosts();
    }

    @GetMapping("/links")
    @PreAuthorize(Roles.ANY)
    public List<SdnDtos.LinkDto> links() {
        return sdn.links();
    }

    @GetMapping("/flows")
    @PreAuthorize(Roles.ANY)
    public List<SdnDtos.FlowDto> flows(@RequestParam(required = false) String deviceId) {
        return sdn.flows(deviceId);
    }

    @DeleteMapping("/flows/{deviceId}/{flowId}")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<Void> deleteFlow(@PathVariable String deviceId, @PathVariable String flowId) {
        sdn.deleteFlow(deviceId, flowId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/policies")
    @PreAuthorize(Roles.ANY)
    public List<SdnDtos.PolicyDto> policies() {
        return sdn.policies();
    }

    @PostMapping("/policies")
    @PreAuthorize(Roles.OPERATE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a network security policy and install it as an OpenFlow rule via ONOS")
    public SdnDtos.PolicyDto createPolicy(@Valid @RequestBody SdnDtos.PolicyRequest req) {
        return sdn.createPolicy(req);
    }

    @PostMapping("/policies/{id}/reinstall")
    @PreAuthorize(Roles.OPERATE)
    public SdnDtos.PolicyDto reinstall(@PathVariable Long id) {
        return sdn.reinstall(id);
    }

    @DeleteMapping("/policies/{id}")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<Void> deletePolicy(@PathVariable Long id) {
        sdn.deletePolicy(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/topology")
    @PreAuthorize(Roles.ANY)
    @Operation(summary = "Topology graph: view=sdn (ONOS) or view=nfv (NFV lab networks)")
    public SdnDtos.Topology topology(@RequestParam(defaultValue = "sdn") String view) {
        return "nfv".equalsIgnoreCase(view) ? topology.nfvView() : topology.sdnView();
    }
}
