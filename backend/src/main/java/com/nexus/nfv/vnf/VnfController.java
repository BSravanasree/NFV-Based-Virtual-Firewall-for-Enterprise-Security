package com.nexus.nfv.vnf;

import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vnfs")
@Tag(name = "VNF Lifecycle", description = "Create, deploy, operate, scale and terminate firewall VNFs")
public class VnfController {

    private final VnfService service;

    public VnfController(VnfService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    public List<VnfDtos.VnfDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY)
    public VnfDtos.VnfDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/{id}/events")
    @PreAuthorize(Roles.ANY)
    public List<VnfDtos.LifecycleEventDto> events(@PathVariable Long id) {
        return service.events(id);
    }

    @GetMapping("/{id}/metrics")
    @PreAuthorize(Roles.ANY)
    @Operation(summary = "Resource metric samples (CPU, memory, throughput) for the last N minutes")
    public List<VnfDtos.MetricSampleDto> metrics(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "60") int minutes) {
        return service.metrics(id, Duration.ofMinutes(Math.min(Math.max(minutes, 5), 7 * 24 * 60)));
    }

    @PostMapping
    @PreAuthorize(Roles.OPERATE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a VNF (state CREATED); no infrastructure is allocated yet")
    public VnfDtos.VnfDto create(@Valid @RequestBody VnfDtos.CreateVnfRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.OPERATE)
    public VnfDtos.VnfDto configure(@PathVariable Long id, @Valid @RequestBody VnfDtos.ConfigureVnfRequest req) {
        return service.configure(id, req);
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize(Roles.OPERATE)
    @Operation(summary = "Instantiate the VNF (asynchronous: 202 + state INSTANTIATING)")
    public ResponseEntity<VnfDtos.VnfDto> deploy(@PathVariable Long id) {
        return ResponseEntity.accepted().body(service.deploy(id));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<VnfDtos.VnfDto> start(@PathVariable Long id) {
        return ResponseEntity.accepted().body(service.start(id));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<VnfDtos.VnfDto> stop(@PathVariable Long id) {
        return ResponseEntity.accepted().body(service.stop(id));
    }

    @PostMapping("/{id}/restart")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<VnfDtos.VnfDto> restart(@PathVariable Long id) {
        return ResponseEntity.accepted().body(service.restart(id));
    }

    @PostMapping("/{id}/scale")
    @PreAuthorize(Roles.OPERATE)
    public VnfDtos.VnfDto scale(@PathVariable Long id, @Valid @RequestBody VnfDtos.ScaleVnfRequest req) {
        return service.scale(id, req);
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<VnfDtos.VnfDto> terminate(@PathVariable Long id) {
        return ResponseEntity.accepted().body(service.terminate(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
