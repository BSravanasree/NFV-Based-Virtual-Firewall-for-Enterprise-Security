package com.nexus.nfv.audit;

import com.nexus.nfv.common.Csv;
import com.nexus.nfv.common.PageResponse;
import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Audit & Reports", description = "Audit trail search, CSV export and PDF security report")
public class AuditController {

    private final AuditService audit;
    private final SecurityReportService reports;

    public AuditController(AuditService audit, SecurityReportService reports) {
        this.audit = audit;
        this.reports = reports;
    }

    @GetMapping("/audit-logs")
    @PreAuthorize(Roles.SECURITY)
    public PageResponse<AuditService.AuditLogDto> search(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return audit.search(new AuditService.AuditQuery(from, to, username, action, category, outcome, search), page, size);
    }

    @GetMapping("/audit-logs/actions")
    @PreAuthorize(Roles.SECURITY)
    public List<String> actions() {
        return audit.actions();
    }

    @GetMapping(value = "/audit-logs/export", produces = "text/csv")
    @PreAuthorize(Roles.SECURITY)
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String search) {
        var rows = audit.export(new AuditService.AuditQuery(from, to, username, action, category, outcome, search), 100_000)
                .stream().map(a -> new Object[]{a.id(), a.occurredAt(), a.username(), a.action(), a.category(),
                        a.targetType(), a.targetId(), a.outcome(), a.ipAddress(), a.details()}).toList();
        String csv = Csv.document(List.of("id", "timestamp", "username", "action", "category", "target_type",
                "target_id", "outcome", "ip_address", "details"), rows);
        audit.success(AuditAction.REPORT_EXPORTED, "REPORT", "AUDIT_LOG", null, rows.size() + " audit records exported (CSV)");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-log.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping(value = "/reports/security.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize(Roles.SECURITY)
    @Operation(summary = "PDF security report for the last N hours")
    public ResponseEntity<byte[]> securityReport(@RequestParam(defaultValue = "24") int hours) {
        byte[] pdf = reports.generate(hours);
        audit.success(AuditAction.REPORT_EXPORTED, "REPORT", "SECURITY_REPORT", null, "PDF security report (" + hours + " h)");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nexus-security-report.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
