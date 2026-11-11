package com.nexus.nfv.alert;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.Csv;
import com.nexus.nfv.common.PageResponse;
import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Security Alerts", description = "Rule-based threat detection results and triage")
public class AlertController {

    private final AlertService service;
    private final ThreatDetectionService detection;
    private final AuditService audit;

    public AlertController(AlertService service, ThreatDetectionService detection, AuditService audit) {
        this.service = service;
        this.detection = detection;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize(Roles.ANY)
    public PageResponse<AlertService.AlertDto> search(
            @RequestParam(required = false) SecurityAlert.Status status,
            @RequestParam(required = false) SecurityAlert.Severity severity,
            @RequestParam(required = false) String detector,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.search(new AlertService.AlertQuery(status, severity, detector, sourceIp, from, to), page, size);
    }

    @GetMapping("/stats")
    @PreAuthorize(Roles.ANY)
    public AlertService.AlertStats stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.ANY)
    public AlertService.AlertDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(Roles.SECURITY)
    @Operation(summary = "Acknowledge, assign, resolve or mark an alert as false positive")
    public AlertService.AlertDto update(@PathVariable Long id, @Valid @RequestBody AlertService.UpdateAlertRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/detect")
    @PreAuthorize(Roles.SECURITY)
    @Operation(summary = "Run the detection engine immediately over the current window")
    public Map<String, Object> detectNow() {
        int n = detection.runDetection(Instant.now());
        return Map.of("alertsCreatedOrUpdated", n, "ranAt", Instant.now());
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize(Roles.ANY)
    public ResponseEntity<byte[]> export(@RequestParam(required = false) SecurityAlert.Status status,
                                         @RequestParam(required = false) SecurityAlert.Severity severity) {
        List<Object[]> rows = service.export(new AlertService.AlertQuery(status, severity, null, null, null, null))
                .stream().map(a -> new Object[]{a.alertId(), a.severity(), a.status(), a.eventType(), a.detector(),
                        a.sourceIp(), a.destinationIp(), a.evidenceCount(), a.firstSeen(), a.lastSeen(),
                        a.assignedTo(), a.description(), a.resolutionNote()}).toList();
        String csv = Csv.document(List.of("alert_id", "severity", "status", "event_type", "detector", "source_ip",
                "destination_ip", "evidence_count", "first_seen", "last_seen", "assigned_to", "description",
                "resolution_note"), rows);
        audit.success(AuditAction.REPORT_EXPORTED, "REPORT", "SECURITY_ALERTS", null, rows.size() + " alerts exported (CSV)");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"security-alerts.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
