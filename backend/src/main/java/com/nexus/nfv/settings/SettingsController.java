package com.nexus.nfv.settings;

import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "System Settings", description = "Detection thresholds and retention policies")
public class SettingsController {

    private final SettingsService service;

    public SettingsController(SettingsService service) {
        this.service = service;
    }

    public record UpdateSettingRequest(@NotNull @Size(max = 255) String value) {
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    public List<SettingsService.SettingDto> list() {
        return service.list();
    }

    @PutMapping("/{key}")
    @PreAuthorize(Roles.ADMIN)
    public SettingsService.SettingDto update(@PathVariable String key, @Valid @RequestBody UpdateSettingRequest req) {
        return service.update(key, req.value());
    }
}
