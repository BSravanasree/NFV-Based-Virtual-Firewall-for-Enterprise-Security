package com.nexus.nfv.settings;

import com.nexus.nfv.audit.AuditLogRepository;
import com.nexus.nfv.traffic.TrafficEventRepository;
import com.nexus.nfv.traffic.TrafficSampleRepository;
import com.nexus.nfv.vnf.VnfMetricSampleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Applies the configured retention to telemetry and audit data (hourly). */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);

    private final SettingsService settings;
    private final TrafficEventRepository events;
    private final TrafficSampleRepository samples;
    private final VnfMetricSampleRepository metrics;
    private final AuditLogRepository audit;

    public RetentionJob(SettingsService settings, TrafficEventRepository events, TrafficSampleRepository samples,
                        VnfMetricSampleRepository metrics, AuditLogRepository audit) {
        this.settings = settings;
        this.events = events;
        this.samples = samples;
        this.metrics = metrics;
        this.audit = audit;
    }

    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void purge() {
        Instant telemetryCutoff = Instant.now().minus(Duration.ofDays(settings.getInt("retention.telemetry.days", 7)));
        Instant auditCutoff = Instant.now().minus(Duration.ofDays(settings.getInt("retention.audit.days", 365)));
        int e = events.deleteOlderThan(telemetryCutoff);
        int s = samples.deleteOlderThan(telemetryCutoff);
        int m = metrics.deleteOlderThan(telemetryCutoff);
        int a = audit.deleteOlderThan(auditCutoff);
        if (e + s + m + a > 0) log.info("Retention purge: {} events, {} traffic samples, {} metric samples, {} audit records", e, s, m, a);
    }
}
