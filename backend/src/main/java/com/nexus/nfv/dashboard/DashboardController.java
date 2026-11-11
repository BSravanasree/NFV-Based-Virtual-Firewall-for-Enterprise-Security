package com.nexus.nfv.dashboard;

import com.nexus.nfv.alert.AlertService;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.firewall.FirewallRuleRepository;
import com.nexus.nfv.integration.IntegrationHealthService;
import com.nexus.nfv.security.Roles;
import com.nexus.nfv.traffic.TrafficDtos;
import com.nexus.nfv.traffic.TrafficService;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfDtos;
import com.nexus.nfv.vnf.VnfEnums;
import com.nexus.nfv.vnf.VnfLifecycleEventRepository;
import com.nexus.nfv.vnf.VnfRepository;
import com.nexus.nfv.vnf.VnfState;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize(Roles.ANY)
@Tag(name = "Dashboard", description = "Aggregated security posture")
public class DashboardController {

    private final VnfRepository vnfs;
    private final VnfLifecycleEventRepository lifecycle;
    private final FirewallRuleRepository rules;
    private final TrafficService traffic;
    private final AlertService alerts;
    private final AuditService audit;
    private final IntegrationHealthService integrations;

    public DashboardController(VnfRepository vnfs, VnfLifecycleEventRepository lifecycle, FirewallRuleRepository rules,
                               TrafficService traffic, AlertService alerts, AuditService audit,
                               IntegrationHealthService integrations) {
        this.vnfs = vnfs;
        this.lifecycle = lifecycle;
        this.rules = rules;
        this.traffic = traffic;
        this.alerts = alerts;
        this.audit = audit;
        this.integrations = integrations;
    }

    public record VnfSummary(long total, long active, long inactive, long failed, long transitional,
                             Map<VnfEnums.Health, Long> byHealth, BigDecimal avgCpuPercent,
                             BigDecimal avgMemoryPercent, long throughputBps, long measuredVnfs) {
    }

    public record RuleSummary(long total, long enabled, long disabled, long allow, long deny) {
    }

    public record Posture(String level, List<String> reasons) {
    }

    public record Summary(Instant generatedAt, VnfSummary vnfs, RuleSummary rules, TrafficDtos.Summary traffic,
                          AlertService.AlertStats alerts, Posture posture,
                          List<IntegrationHealthService.ConnectionDto> integrations) {
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public Summary summary(@RequestParam(defaultValue = "60") int minutes) {
        List<Vnf> all = vnfs.findAll();
        long active = all.stream().filter(v -> v.getState() == VnfState.ACTIVE).count();
        long failed = all.stream().filter(v -> v.getState() == VnfState.FAILED).count();
        long transitional = all.stream().filter(v -> v.getState().isTransitional()).count();
        long terminated = all.stream().filter(v -> v.getState() == VnfState.TERMINATED).count();
        Map<VnfEnums.Health, Long> byHealth = new EnumMap<>(VnfEnums.Health.class);
        for (VnfEnums.Health h : VnfEnums.Health.values()) byHealth.put(h, 0L);
        all.stream().filter(v -> v.getState() == VnfState.ACTIVE).forEach(v -> byHealth.merge(v.getHealth(), 1L, Long::sum));
        List<Vnf> measured = all.stream().filter(v -> v.getState() == VnfState.ACTIVE && v.getCpuPercent() != null).toList();
        BigDecimal avgCpu = avg(measured.stream().map(Vnf::getCpuPercent).toList());
        BigDecimal avgMem = avg(measured.stream().map(Vnf::getMemoryPercent).filter(Objects::nonNull).toList());
        long tput = measured.stream().map(Vnf::getThroughputBps).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        VnfSummary vs = new VnfSummary(all.size() - terminated, active, all.size() - terminated - active, failed,
                transitional, byHealth, avgCpu, avgMem, tput, measured.size());

        long allow = 0, deny = 0, enabled = 0, total = 0;
        for (var c : rules.countByActionAndEnabled()) {
            total += c.getTotal();
            if (c.getEnabled()) enabled += c.getTotal();
            if (c.getAction() == com.nexus.nfv.firewall.FirewallRule.Action.ALLOW) allow += c.getTotal();
            else deny += c.getTotal();
        }
        RuleSummary rs = new RuleSummary(total, enabled, total - enabled, allow, deny);
        TrafficDtos.Summary ts = traffic.summary(Duration.ofMinutes(Math.max(5, Math.min(minutes, 1440))), null);
        AlertService.AlertStats as = alerts.stats();
        return new Summary(Instant.now(), vs, rs, ts, as, posture(all, as), integrations.list());
    }

    @GetMapping("/activity")
    @Transactional(readOnly = true)
    public Map<String, Object> activity() {
        // the audit trail is restricted to ADMIN / SECURITY_ANALYST (same rule as /audit-logs)
        boolean mayReadAudit = com.nexus.nfv.security.CurrentUser.principal()
                .map(p -> p.role() == com.nexus.nfv.user.RoleName.ADMIN
                        || p.role() == com.nexus.nfv.user.RoleName.SECURITY_ANALYST)
                .orElse(false);
        return Map.of(
                "lifecycle", lifecycle.findTop15ByOrderByOccurredAtDesc().stream().map(VnfDtos.LifecycleEventDto::from).toList(),
                "alerts", alerts.recent(),
                "audit", mayReadAudit ? audit.recent() : List.of());
    }

    /** Transparent posture rules: every reason shown in the UI is one of these conditions. */
    static Posture posture(List<Vnf> all, AlertService.AlertStats as) {
        List<String> reasons = new java.util.ArrayList<>();
        String level = "SECURE";
        long critical = as.activeBySeverity().getOrDefault(com.nexus.nfv.alert.SecurityAlert.Severity.CRITICAL, 0L);
        long high = as.activeBySeverity().getOrDefault(com.nexus.nfv.alert.SecurityAlert.Severity.HIGH, 0L);
        if (critical > 0) {
            level = "CRITICAL";
            reasons.add(critical + " active critical alert(s)");
        }
        if (high > 0) {
            if (!level.equals("CRITICAL")) level = "AT_RISK";
            reasons.add(high + " active high-severity alert(s)");
        }
        long failed = all.stream().filter(v -> v.getState() == VnfState.FAILED).count();
        if (failed > 0) {
            if (level.equals("SECURE")) level = "AT_RISK";
            reasons.add(failed + " VNF(s) in FAILED state");
        }
        long unsynced = all.stream().filter(v -> v.getState() == VnfState.ACTIVE
                && v.getSyncStatus() != VnfEnums.SyncStatus.IN_SYNC).count();
        if (unsynced > 0) {
            if (level.equals("SECURE")) level = "DEGRADED";
            reasons.add(unsynced + " active firewall(s) not in sync with desired policy");
        }
        long open = all.stream().filter(v -> v.getState() == VnfState.ACTIVE
                && v.getDefaultAction() == VnfEnums.DefaultAction.ACCEPT).count();
        if (open > 0) {
            if (level.equals("SECURE")) level = "DEGRADED";
            reasons.add(open + " active firewall(s) with default-ACCEPT policy");
        }
        if (all.stream().noneMatch(v -> v.getState() == VnfState.ACTIVE)) {
            if (level.equals("SECURE")) level = "UNPROTECTED";
            reasons.add("No active firewall VNF");
        }
        if (reasons.isEmpty()) reasons.add("All active firewalls healthy, in sync and default-deny; no high/critical alerts");
        return new Posture(level, reasons);
    }

    private static BigDecimal avg(List<BigDecimal> l) {
        if (l.isEmpty()) return null;
        return l.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(l.size()), 2, RoundingMode.HALF_UP);
    }
}
