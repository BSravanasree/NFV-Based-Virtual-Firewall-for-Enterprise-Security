package com.nexus.nfv.netconf;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/netconf/devices")
@Tag(name = "NETCONF", description = "YANG-modelled firewall configuration over NETCONF (RFC 6241)")
public class NetconfController {

    private final NetconfService service;

    public NetconfController(NetconfService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    public List<NetconfDtos.DeviceDto> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY)
    public NetconfDtos.DeviceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize(Roles.OPERATE)
    @ResponseStatus(HttpStatus.CREATED)
    public NetconfDtos.DeviceDto create(@Valid @RequestBody NetconfDtos.DeviceRequest req) {
        return service.create(req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.OPERATE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/test")
    @PreAuthorize(Roles.OPERATE)
    @Operation(summary = "Open a NETCONF session, exchange <hello>, record capabilities and pin the host key")
    public NetconfDtos.DeviceDto test(@PathVariable Long id) {
        return service.test(id);
    }

    @PostMapping("/{id}/reset-host-key")
    @PreAuthorize(Roles.ADMIN)
    public NetconfDtos.DeviceDto resetHostKey(@PathVariable Long id) {
        return service.resetHostKey(id);
    }

    @GetMapping("/{id}/running")
    @PreAuthorize(Roles.ANY)
    @Operation(summary = "<get-config> of the nexus-firewall subtree from the running datastore")
    public NetconfDtos.RunningConfig running(@PathVariable Long id) {
        return service.running(id);
    }

    @GetMapping("/{id}/desired")
    @PreAuthorize(Roles.ANY)
    public Map<String, String> desired(@PathVariable Long id) {
        return Map.of("configXml", service.desiredXml(id));
    }

    @PostMapping("/{id}/snapshot")
    @PreAuthorize(Roles.OPERATE)
    public NetconfDtos.VersionDto snapshot(@PathVariable Long id) {
        return service.snapshot(id);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize(Roles.OPERATE)
    public NetconfDtos.OperationResult validate(@PathVariable Long id) {
        return service.validate(id);
    }

    @PostMapping("/{id}/push")
    @PreAuthorize(Roles.OPERATE)
    @Operation(summary = "Push the linked VNF's firewall policy (lock/edit/validate/commit)")
    public NetconfDtos.OperationResult push(@PathVariable Long id) {
        return service.push(id);
    }

    @GetMapping("/{id}/sync")
    @PreAuthorize(Roles.ANY)
    public NetconfDtos.SyncReport sync(@PathVariable Long id) {
        return service.sync(id);
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize(Roles.ANY)
    public List<NetconfDtos.VersionDto> history(@PathVariable Long id) {
        return service.history(id);
    }

    @GetMapping("/{id}/versions/{versionId}")
    @PreAuthorize(Roles.ANY)
    public NetconfDtos.VersionContent version(@PathVariable Long id, @PathVariable Long versionId) {
        return service.version(id, versionId);
    }

    @PostMapping("/{id}/versions/{versionId}/rollback")
    @PreAuthorize(Roles.OPERATE)
    public NetconfDtos.OperationResult rollback(@PathVariable Long id, @PathVariable Long versionId) {
        return service.rollback(id, versionId);
    }
}
