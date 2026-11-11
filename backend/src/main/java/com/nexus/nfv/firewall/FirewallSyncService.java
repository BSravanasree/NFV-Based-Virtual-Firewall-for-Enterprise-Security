package com.nexus.nfv.firewall;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.firewall.agent.AgentModels;
import com.nexus.nfv.firewall.agent.FirewallAgentClient;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import com.nexus.nfv.vnf.VnfEvents;
import com.nexus.nfv.vnf.VnfRepository;
import com.nexus.nfv.vnf.VnfState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Desired-state reconciliation between the database (source of truth) and the nftables ruleset inside each
 * firewall VNF. A push is only marked IN_SYNC after the agent confirms the exact revision was committed by
 * {@code nft -f} (atomic transaction inside the VNF).
 */
@Service
public class FirewallSyncService {

    private static final Logger log = LoggerFactory.getLogger(FirewallSyncService.class);

    private final VnfRepository vnfs;
    private final FirewallRuleRepository rules;
    private final FirewallAgentClient agent;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FirewallSyncService(VnfRepository vnfs, FirewallRuleRepository rules, FirewallAgentClient agent,
                               AuditService audit, PlatformTransactionManager txm) {
        this.vnfs = vnfs;
        this.rules = rules;
        this.agent = agent;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
        // REQUIRES_NEW: this service is also invoked from AFTER_COMMIT listeners, where joining the
        // completed outer transaction would silently discard writes.
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Translates the desired policy into the agent wire format (enabled rules, ascending priority). */
    public static AgentModels.Ruleset toRuleset(Vnf vnf, List<FirewallRule> enabledRules) {
        List<AgentModels.Rule> list = enabledRules.stream()
                .filter(FirewallRule::isEnabled)
                .sorted((a, b) -> Integer.compare(a.getPriority(), b.getPriority()))
                .map(r -> new AgentModels.Rule(r.getId(), r.getPriority(),
                        r.getAction() == FirewallRule.Action.ALLOW ? "accept" : "drop",
                        r.getProtocol().name().toLowerCase(), r.getSourceCidr(), r.getDestinationCidr(),
                        r.getSourcePort(), r.getDestinationPort(), r.isLogEnabled()))
                .toList();
        return new AgentModels.Ruleset(vnf.getRulesRevision(),
                vnf.getDefaultAction() == VnfEnums.DefaultAction.ACCEPT ? "accept" : "drop", list);
    }

    /** Pushes the desired policy if the VNF is ACTIVE; otherwise reports PENDING (applied on activation). */
    public FirewallDtos.SyncResult applyIfActive(Long vnfId) {
        Vnf vnf = vnfs.findById(vnfId).orElseThrow(() -> ApiException.notFound("VNF", vnfId));
        if (vnf.getState() != VnfState.ACTIVE) {
            return new FirewallDtos.SyncResult(false, false, vnf.getSyncStatus(), vnf.getRulesRevision(),
                    vnf.getAppliedRevision(), "VNF is " + vnf.getState()
                    + "; policy saved and will be applied when the VNF becomes ACTIVE");
        }
        return apply(vnfId, false);
    }

    public FirewallDtos.SyncResult apply(Long vnfId, boolean auditSuccess) {
        ReentrantLock lock = locks.computeIfAbsent(vnfId, k -> new ReentrantLock());
        lock.lock();
        try {
            record Snapshot(Vnf vnf, AgentModels.Ruleset ruleset) {
            }
            Snapshot snap = tx.execute(s -> {
                Vnf v = vnfs.findById(vnfId).orElseThrow(() -> ApiException.notFound("VNF", vnfId));
                if (v.getState() != VnfState.ACTIVE) {
                    throw ApiException.invalidState("Policy can only be applied to an ACTIVE VNF (state " + v.getState() + ")");
                }
                return new Snapshot(v, toRuleset(v, rules.findByVnfIdAndEnabledTrueOrderByPriorityAsc(vnfId)));
            });
            try {
                AgentModels.ApplyResult res = agent.applyRuleset(snap.vnf().getAgentUrl(), snap.ruleset());
                if (res.appliedRevision() != snap.ruleset().revision()) {
                    throw new IllegalStateException("agent confirmed revision " + res.appliedRevision()
                            + " but " + snap.ruleset().revision() + " was sent");
                }
                Instant now = Instant.now();
                FirewallDtos.SyncResult result = tx.execute(s -> {
                    Vnf v = vnfs.findById(vnfId).orElseThrow();
                    v.setAppliedRevision(res.appliedRevision());
                    v.setRulesAppliedAt(now);
                    v.setSyncStatus(v.getRulesRevision() == res.appliedRevision()
                            ? VnfEnums.SyncStatus.IN_SYNC : VnfEnums.SyncStatus.PENDING);
                    v.setLastErrorTruncated(null);
                    vnfs.save(v);
                    return new FirewallDtos.SyncResult(true, true, v.getSyncStatus(), v.getRulesRevision(),
                            res.appliedRevision(), "Applied revision " + res.appliedRevision() + " ("
                            + res.ruleCount() + " rules) to " + v.getName());
                });
                if (auditSuccess) {
                    audit.success(AuditAction.FW_POLICY_APPLIED, "FIREWALL", "VNF", vnfId, result.message());
                }
                return result;
            } catch (RuntimeException ex) {
                log.warn("Policy push to VNF {} failed: {}", snap.vnf().getName(), ex.getMessage());
                tx.executeWithoutResult(s -> vnfs.findById(vnfId).ifPresent(v -> {
                    v.setSyncStatus(VnfEnums.SyncStatus.FAILED);
                    v.setLastErrorTruncated("Policy push failed: " + ex.getMessage());
                    vnfs.save(v);
                }));
                audit.record(AuditAction.FW_POLICY_APPLIED, "FIREWALL", "VNF", vnfId, false,
                        "Revision " + snap.ruleset().revision() + " NOT applied: " + ex.getMessage());
                return new FirewallDtos.SyncResult(true, false, VnfEnums.SyncStatus.FAILED,
                        snap.ruleset().revision(), snap.vnf().getAppliedRevision(), "Not applied: " + ex.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    /** VNF became ACTIVE (deploy/start/restart/policy default change): push desired state. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onActivated(VnfEvents.Activated event) {
        try {
            FirewallDtos.SyncResult r = apply(event.vnfId(), true);
            log.info("Initial/updated policy for VNF {}: {}", event.vnfId(), r.message());
        } catch (RuntimeException ex) {
            log.warn("Policy apply after activation of VNF {} failed: {}", event.vnfId(), ex.getMessage());
        }
    }

    /**
     * Drift detection: every 30 s compare the agent's applied revision with the desired one and re-apply
     * when they diverge (e.g. VNF restarted outside the platform, or an earlier push failed).
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 20000)
    public void reconcile() {
        for (Vnf v : vnfs.findByStateIn(EnumSet.of(VnfState.ACTIVE))) {
            try {
                AgentModels.Health h = agent.health(v.getAgentUrl());
                if (h.appliedRevision() != v.getRulesRevision()) {
                    if (v.getSyncStatus() == VnfEnums.SyncStatus.IN_SYNC) {
                        tx.executeWithoutResult(s -> vnfs.findById(v.getId()).ifPresent(f -> {
                            f.setSyncStatus(VnfEnums.SyncStatus.DRIFT);
                            vnfs.save(f);
                        }));
                    }
                    log.info("VNF {} drift: agent revision {} != desired {} (source={}); re-applying",
                            v.getName(), h.appliedRevision(), v.getRulesRevision(), h.rulesSource());
                    apply(v.getId(), false);
                }
            } catch (RuntimeException ex) {
                log.debug("Reconcile of {} skipped: {}", v.getName(), ex.getMessage());
            }
        }
    }
}
