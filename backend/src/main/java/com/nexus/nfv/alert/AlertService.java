package com.nexus.nfv.alert;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.PageResponse;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
public class AlertService {

    private final SecurityAlertRepository repo;
    private final AuditService audit;

    public AlertService(SecurityAlertRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public record AlertDto(Long id, String alertId, Long vnfId, String detector, String eventType,
                           SecurityAlert.Severity severity, SecurityAlert.Status status, String sourceIp,
                           String destinationIp, String description, int evidenceCount, Instant firstSeen,
                           Instant lastSeen, String assignedTo, String resolutionNote, Instant createdAt,
                           Instant updatedAt) {
        public static AlertDto from(SecurityAlert a) {
            return new AlertDto(a.getId(), String.format("ALT-%06d", a.getId()), a.getVnfId(), a.getDetector(),
                    a.getEventType(), a.getSeverity(), a.getStatus(), a.getSourceIp(), a.getDestinationIp(),
                    a.getDescription(), a.getEvidenceCount(), a.getFirstSeen(), a.getLastSeen(), a.getAssignedTo(),
                    a.getResolutionNote(), a.getCreatedAt(), a.getUpdatedAt());
        }
    }

    public record AlertQuery(SecurityAlert.Status status, SecurityAlert.Severity severity, String detector,
                             String sourceIp, Instant from, Instant to) {
    }

    public record UpdateAlertRequest(@NotNull SecurityAlert.Status status, @Size(max = 64) String assignedTo,
                                     @Size(max = 1000) String note) {
    }

    public record AlertStats(long open, long acknowledged, Map<SecurityAlert.Severity, Long> activeBySeverity) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertDto> search(AlertQuery q, int page, int size) {
        return PageResponse.of(repo.findAll(spec(q), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "lastSeen"))), AlertDto::from);
    }

    @Transactional(readOnly = true)
    public List<AlertDto> export(AlertQuery q) {
        return repo.findAll(spec(q), PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "lastSeen")))
                .map(AlertDto::from).getContent();
    }

    @Transactional(readOnly = true)
    public AlertDto get(Long id) {
        return AlertDto.from(find(id));
    }

    @Transactional(readOnly = true)
    public AlertStats stats() {
        Map<SecurityAlert.Severity, Long> bySev = new EnumMap<>(SecurityAlert.Severity.class);
        for (SecurityAlert.Severity s : SecurityAlert.Severity.values()) bySev.put(s, 0L);
        repo.countOpenBySeverity(EnumSet.of(SecurityAlert.Status.OPEN, SecurityAlert.Status.ACKNOWLEDGED))
                .forEach(c -> bySev.put(c.getSeverity(), c.getTotal()));
        return new AlertStats(repo.countByStatusIn(EnumSet.of(SecurityAlert.Status.OPEN)),
                repo.countByStatusIn(EnumSet.of(SecurityAlert.Status.ACKNOWLEDGED)), bySev);
    }

    @Transactional(readOnly = true)
    public List<AlertDto> recent() {
        return repo.findTop10ByOrderByLastSeenDesc().stream().map(AlertDto::from).toList();
    }

    @Transactional
    public AlertDto update(Long id, UpdateAlertRequest req) {
        SecurityAlert a = find(id);
        SecurityAlert.Status old = a.getStatus();
        boolean closing = req.status() == SecurityAlert.Status.RESOLVED || req.status() == SecurityAlert.Status.FALSE_POSITIVE;
        if (closing && (req.note() == null || req.note().isBlank())) {
            throw ApiException.badRequest("A resolution note is required to resolve or dismiss an alert");
        }
        a.setStatus(req.status());
        if (req.assignedTo() != null) a.setAssignedTo(req.assignedTo().isBlank() ? null : req.assignedTo().trim());
        else if (req.status() == SecurityAlert.Status.ACKNOWLEDGED && a.getAssignedTo() == null) {
            a.setAssignedTo(CurrentUser.username());
        }
        if (req.note() != null && !req.note().isBlank()) a.setResolutionNote(req.note().trim());
        repo.save(a);
        audit.success(AuditAction.ALERT_UPDATED, "ALERT", "SECURITY_ALERT", id,
                String.format("ALT-%06d %s: %s -> %s", id, a.getDetector(), old, req.status()));
        return AlertDto.from(a);
    }

    private SecurityAlert find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Alert", id));
    }

    static Specification<SecurityAlert> spec(AlertQuery q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q.status() != null) p.add(cb.equal(root.get("status"), q.status()));
            if (q.severity() != null) p.add(cb.equal(root.get("severity"), q.severity()));
            if (q.detector() != null && !q.detector().isBlank()) p.add(cb.equal(root.get("detector"), q.detector()));
            if (q.sourceIp() != null && !q.sourceIp().isBlank()) p.add(cb.equal(root.get("sourceIp"), q.sourceIp().trim()));
            if (q.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("lastSeen"), q.from()));
            if (q.to() != null) p.add(cb.lessThanOrEqualTo(root.get("lastSeen"), q.to()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
