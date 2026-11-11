package com.nexus.nfv.traffic;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/traffic")
@PreAuthorize(Roles.ANY)
@Tag(name = "Traffic Monitoring", description = "nftables counters and NFLOG connection events from firewall VNFs")
public class TrafficController {

    private final TrafficService service;
    private final AuditService audit;

    public TrafficController(TrafficService service, AuditService audit) {
        this.service = service;
        this.audit = audit;
    }

    @GetMapping("/summary")
    public TrafficDtos.Summary summary(@RequestParam(defaultValue = "60") int minutes,
                                       @RequestParam(required = false) Long vnfId) {
        return service.summary(window(minutes), vnfId);
    }

    @GetMapping("/timeseries")
    @Operation(summary = "Bucketed counter deltas (packets allowed/blocked, throughput, protocol mix)")
    public List<TrafficDtos.TimePoint> timeseries(@RequestParam(defaultValue = "60") int minutes,
                                                  @RequestParam(defaultValue = "60") int bucketSeconds,
                                                  @RequestParam(required = false) Long vnfId) {
        return service.timeseries(window(minutes), bucketSeconds, vnfId);
    }

    @GetMapping("/top")
    public TrafficDtos.TopTalkers top(@RequestParam(defaultValue = "60") int minutes,
                                      @RequestParam(defaultValue = "10") int limit) {
        return service.top(window(minutes), limit);
    }

    @GetMapping("/events")
    public PageResponse<TrafficDtos.EventDto> events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long vnfId,
            @RequestParam(required = false) TrafficEvent.Action action,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) String dstIp,
            @RequestParam(required = false) Integer port,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.searchEvents(new TrafficDtos.EventQuery(from, to, vnfId, action, protocol, srcIp, dstIp, port,
                direction), page, size);
    }

    @GetMapping(value = "/events/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Long vnfId,
            @RequestParam(required = false) TrafficEvent.Action action,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String srcIp,
            @RequestParam(required = false) String dstIp,
            @RequestParam(required = false) Integer port,
            @RequestParam(required = false) String direction) {
        var q = new TrafficDtos.EventQuery(from, to, vnfId, action, protocol, srcIp, dstIp, port, direction);
        List<Object[]> rows = service.exportEvents(q, 50_000).stream().map(e -> new Object[]{
                e.occurredAt(), e.vnfId(), e.action(), e.protocol(), e.srcIp(), e.srcPort(), e.dstIp(), e.dstPort(),
                e.ruleId() == null ? "default-policy" : e.ruleId(), e.direction(), e.packetLength(), e.dataSource()
        }).toList();
        String csv = Csv.document(List.of("timestamp", "vnf_id", "action", "protocol", "src_ip", "src_port", "dst_ip",
                "dst_port", "rule", "direction", "packet_length", "data_source"), rows);
        audit.success(AuditAction.REPORT_EXPORTED, "REPORT", "TRAFFIC_EVENTS", null, rows.size() + " traffic events exported (CSV)");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"traffic-events.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    private static Duration window(int minutes) {
        return Duration.ofMinutes(Math.min(Math.max(minutes, 5), 7 * 24 * 60));
    }
}
