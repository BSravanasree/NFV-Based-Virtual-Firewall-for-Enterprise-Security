package com.nexus.nfv.alert;

import com.nexus.nfv.settings.SettingsService;
import com.nexus.nfv.traffic.TrafficEvent;
import com.nexus.nfv.traffic.TrafficEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Transparent, rule-based detection engine (not machine learning). Each detector is a threshold over
 * firewall NFLOG events inside a sliding window; thresholds are editable in System Settings.
 *
 * <table>
 *   <tr><th>Detector</th><th>Signal</th><th>Severity</th></tr>
 *   <tr><td>PORT_SCAN</td><td>distinct destination ports from one source ≥ N</td><td>HIGH (CRITICAL ≥ 5N)</td></tr>
 *   <tr><td>REPEATED_BLOCKED</td><td>blocked packets from one source ≥ T</td><td>MEDIUM (HIGH ≥ 5T)</td></tr>
 *   <tr><td>HIGH_FREQUENCY</td><td>logged attempts from one source ≥ F</td><td>MEDIUM</td></tr>
 *   <tr><td>UNAUTHORIZED_ACCESS</td><td>blocked attempts to sensitive ports ≥ B</td><td>HIGH (CRITICAL ≥ 4B)</td></tr>
 *   <tr><td>UNEXPECTED_COMMUNICATION</td><td>flows hitting the default-deny policy (no rule matched)</td><td>LOW</td></tr>
 * </table>
 */
@Service
public class ThreatDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ThreatDetectionService.class);
    static final List<Integer> SENSITIVE_PORTS = List.of(22, 23, 445, 3306, 3389, 5432, 1433, 5900);
    private static final EnumSet<SecurityAlert.Status> ACTIVE = EnumSet.of(SecurityAlert.Status.OPEN,
            SecurityAlert.Status.ACKNOWLEDGED);

    private final TrafficEventRepository events;
    private final SecurityAlertRepository alerts;
    private final SettingsService settings;

    public ThreatDetectionService(TrafficEventRepository events, SecurityAlertRepository alerts,
                                  SettingsService settings) {
        this.events = events;
        this.alerts = alerts;
        this.settings = settings;
    }

    @Scheduled(fixedDelayString = "${nexus.monitoring.detection-interval-ms:15000}", initialDelay = 30000)
    public void scheduledRun() {
        if (!settings.getBool("detection.enabled", true)) return;
        try {
            int created = runDetection(Instant.now());
            if (created > 0) log.info("Threat detection raised/updated {} alert(s)", created);
        } catch (RuntimeException ex) {
            log.warn("Threat detection cycle failed: {}", ex.getMessage());
        }
    }

    /** Runs all detectors over the window ending at {@code now}. Returns the number of alerts created/updated. */
    @Transactional
    public int runDetection(Instant now) {
        Duration window = Duration.ofSeconds(settings.getInt("detection.window.seconds", 300));
        Instant since = now.minus(window);
        int portScan = settings.getInt("detection.portscan.distinct_ports", 10);
        int blocked = settings.getInt("detection.blocked.threshold", 20);
        int frequency = settings.getInt("detection.frequency.threshold", 200);
        int brute = settings.getInt("detection.bruteforce.threshold", 5);
        int n = 0;

        for (var s : events.sourceStats(since, null)) {
            if (s.getDistinctPorts() >= portScan) {
                n += raise("PORT_SCAN", "Port scan", s.getDistinctPorts() >= 5L * portScan
                                ? SecurityAlert.Severity.CRITICAL : SecurityAlert.Severity.HIGH, s,
                        s.getSrcIp() + " probed " + s.getDistinctPorts() + " distinct destination ports within "
                                + window.toSeconds() + "s (threshold " + portScan + ")", window);
            }
            if (s.getTotal() >= frequency) {
                n += raise("HIGH_FREQUENCY", "Unusual connection frequency", SecurityAlert.Severity.MEDIUM, s,
                        s.getSrcIp() + " generated " + s.getTotal() + " logged connection attempts within "
                                + window.toSeconds() + "s (threshold " + frequency + ")", window);
            }
        }
        for (var s : events.sourceStats(since, TrafficEvent.Action.BLOCKED)) {
            if (s.getTotal() >= blocked) {
                n += raise("REPEATED_BLOCKED", "Repeated blocked traffic", s.getTotal() >= 5L * blocked
                                ? SecurityAlert.Severity.HIGH : SecurityAlert.Severity.MEDIUM, s,
                        s.getTotal() + " packets from " + s.getSrcIp() + " were blocked by the firewall within "
                                + window.toSeconds() + "s (threshold " + blocked + ")", window);
            }
        }
        for (var s : events.blockedToPorts(since, SENSITIVE_PORTS)) {
            if (s.getTotal() >= brute) {
                n += raise("UNAUTHORIZED_ACCESS", "Unauthorized access attempt", s.getTotal() >= 4L * brute
                                ? SecurityAlert.Severity.CRITICAL : SecurityAlert.Severity.HIGH, s,
                        s.getSrcIp() + " made " + s.getTotal() + " blocked attempts to sensitive service ports "
                                + SENSITIVE_PORTS + " (e.g. " + s.getSampleDst() + ")", window);
            }
        }
        for (var s : events.defaultPolicyDrops(since)) {
            n += raise("UNEXPECTED_COMMUNICATION", "Unexpected network communication", SecurityAlert.Severity.LOW, s,
                    s.getSrcIp() + " attempted " + s.getTotal() + " connection(s) to " + s.getDistinctPorts()
                            + " destination(s) not permitted by any rule (default-deny)", window);
        }
        return n;
    }

    /** Creates an alert or updates the active one for the same detector + source (deduplication). */
    private int raise(String detector, String eventType, SecurityAlert.Severity severity,
                      TrafficEventRepository.SourceStats s, String description, Duration window) {
        var existing = alerts.findFirstByDetectorAndSourceIpAndStatusInOrderByLastSeenDesc(detector, s.getSrcIp(), ACTIVE);
        if (existing.isPresent()) {
            SecurityAlert a = existing.get();
            if (!s.getLastSeen().isAfter(a.getLastSeen())) return 0; // nothing new
            a.setLastSeen(s.getLastSeen());
            a.setEvidenceCount((int) Math.min(Integer.MAX_VALUE, Math.max(a.getEvidenceCount(), s.getTotal())));
            if (severity.ordinal() < a.getSeverity().ordinal()) a.setSeverity(severity); // escalate only
            a.setDescription(description);
            alerts.save(a);
            return 1;
        }
        SecurityAlert a = new SecurityAlert();
        a.setDetector(detector);
        a.setEventType(eventType);
        a.setSeverity(severity);
        a.setStatus(SecurityAlert.Status.OPEN);
        a.setSourceIp(s.getSrcIp());
        a.setDestinationIp(s.getSampleDst());
        a.setVnfId(s.getVnfId());
        a.setDescription(description);
        a.setEvidenceCount((int) Math.min(Integer.MAX_VALUE, s.getTotal()));
        a.setFirstSeen(s.getFirstSeen());
        a.setLastSeen(s.getLastSeen());
        alerts.save(a);
        return 1;
    }
}
