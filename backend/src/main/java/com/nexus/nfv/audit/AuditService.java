package com.nexus.nfv.audit;

import com.nexus.nfv.common.PageResponse;
import com.nexus.nfv.security.CurrentUser;
import com.nexus.nfv.security.NexusPrincipal;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository repository;
    private final TransactionTemplate requiresNew;

    public AuditService(AuditLogRepository repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Records an action by the current principal. Runs in its own transaction so that
     * failed operations (whose business transaction rolls back) are still audited. */
    public void record(String action, String category, String targetType, Object targetId,
                       boolean success, String details) {
        NexusPrincipal p = CurrentUser.principal().orElse(null);
        write(p == null ? null : p.userId(), p == null ? CurrentUser.SYSTEM : p.username(),
                action, category, targetType, targetId, success, details);
    }

    public void recordAs(Long userId, String username, String action, String category, String targetType,
                         Object targetId, boolean success, String details) {
        write(userId, username, action, category, targetType, targetId, success, details);
    }

    public void success(String action, String category, String targetType, Object targetId, String details) {
        record(action, category, targetType, targetId, true, details);
    }

    private void write(Long userId, String username, String action, String category, String targetType,
                       Object targetId, boolean success, String details) {
        try {
            AuditLog a = new AuditLog();
            a.setOccurredAt(Instant.now());
            a.setUserId(userId);
            a.setUsername(username);
            a.setAction(action);
            a.setCategory(category);
            a.setTargetType(targetType);
            a.setTargetId(targetId == null ? null : String.valueOf(targetId));
            a.setOutcome(success ? "SUCCESS" : "FAILURE");
            a.setDetails(details == null ? null : (details.length() > 2000 ? details.substring(0, 2000) : details));
            a.setIpAddress(CurrentUser.clientIp().orElse(null));
            requiresNew.executeWithoutResult(status -> repository.save(a));
        } catch (RuntimeException ex) {
            // Auditing must never break the business operation, but the failure must be visible.
            log.error("Failed to persist audit record action={} user={}", action, username, ex);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogDto> search(AuditQuery q, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200),
                Sort.by(Sort.Direction.DESC, "occurredAt"));
        return PageResponse.of(repository.findAll(spec(q), pageable), AuditLogDto::from);
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> export(AuditQuery q, int limit) {
        return repository.findAll(spec(q), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(AuditLogDto::from).getContent();
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> recent() {
        return repository.findTop10ByOrderByOccurredAtDesc().stream().map(AuditLogDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> actions() {
        return repository.distinctActions();
    }

    static Specification<AuditLog> spec(AuditQuery q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), q.from()));
            if (q.to() != null) p.add(cb.lessThanOrEqualTo(root.get("occurredAt"), q.to()));
            if (notBlank(q.username())) p.add(cb.equal(root.get("username"), q.username()));
            if (notBlank(q.action())) p.add(cb.equal(root.get("action"), q.action()));
            if (notBlank(q.category())) p.add(cb.equal(root.get("category"), q.category()));
            if (notBlank(q.outcome())) p.add(cb.equal(root.get("outcome"), q.outcome()));
            if (notBlank(q.search())) {
                String like = "%" + q.search().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("details")), like),
                        cb.like(cb.lower(root.get("targetId")), like),
                        cb.like(cb.lower(root.get("targetType")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public record AuditQuery(Instant from, Instant to, String username, String action, String category,
                             String outcome, String search) {
    }

    public record AuditLogDto(Long id, Instant occurredAt, String username, String action, String category,
                              String targetType, String targetId, String outcome, String details,
                              String ipAddress) {
        static AuditLogDto from(AuditLog a) {
            return new AuditLogDto(a.getId(), a.getOccurredAt(), a.getUsername(), a.getAction(), a.getCategory(),
                    a.getTargetType(), a.getTargetId(), a.getOutcome(), a.getDetails(), a.getIpAddress());
        }
    }
}
