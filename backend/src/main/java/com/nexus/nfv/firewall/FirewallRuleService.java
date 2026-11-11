package com.nexus.nfv.firewall;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.common.net.Ipv4;
import com.nexus.nfv.firewall.agent.AgentModels;
import com.nexus.nfv.firewall.agent.FirewallAgentClient;
import com.nexus.nfv.security.CurrentUser;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import com.nexus.nfv.vnf.VnfRepository;
import com.nexus.nfv.vnf.VnfState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** CRUD and validation of firewall rules (desired state). Every mutation bumps the VNF policy revision. */
@Service
public class FirewallRuleService {

    private final FirewallRuleRepository rules;
    private final VnfRepository vnfs;
    private final AuditService audit;
    private final FirewallAgentClient agent;

    public FirewallRuleService(FirewallRuleRepository rules, VnfRepository vnfs, AuditService audit,
                               FirewallAgentClient agent) {
        this.rules = rules;
        this.vnfs = vnfs;
        this.audit = audit;
        this.agent = agent;
    }

    @Transactional(readOnly = true)
    public FirewallDtos.PolicyDto policy(Long vnfId) {
        Vnf v = vnf(vnfId);
        return new FirewallDtos.PolicyDto(v.getId(), v.getName(), v.getState().name(), v.getDefaultAction(),
                v.getRulesRevision(), v.getAppliedRevision(), v.getRulesAppliedAt(), v.getSyncStatus(),
                rules.findByVnfIdOrderByPriorityAsc(vnfId).stream().map(FirewallDtos.RuleDto::from).toList());
    }

    @Transactional(readOnly = true)
    public FirewallDtos.RuleDto get(Long ruleId) {
        return FirewallDtos.RuleDto.from(rule(ruleId));
    }

    @Transactional
    public FirewallDtos.RuleDto create(Long vnfId, FirewallDtos.RuleRequest req) {
        Vnf v = editableVnf(vnfId);
        FirewallRule r = new FirewallRule();
        r.setVnfId(vnfId);
        r.setCreatedBy(CurrentUser.username());
        apply(r, req);
        if (rules.existsByVnfIdAndName(vnfId, r.getName())) throw ApiException.conflict("Rule name already used on this VNF");
        rules.findByVnfIdAndPriority(vnfId, r.getPriority()).ifPresent(x -> {
            throw ApiException.conflict("Priority " + r.getPriority() + " is already used by rule '" + x.getName() + "'");
        });
        rules.save(r);
        bump(v);
        audit.success(AuditAction.FW_RULE_CREATED, "FIREWALL", "FIREWALL_RULE", r.getId(),
                "VNF " + v.getName() + ": created '" + r.getName() + "' " + r.summary());
        return FirewallDtos.RuleDto.from(r);
    }

    @Transactional
    public FirewallDtos.RuleDto update(Long ruleId, FirewallDtos.RuleRequest req) {
        FirewallRule r = rule(ruleId);
        Vnf v = editableVnf(r.getVnfId());
        String before = r.summary();
        String oldName = r.getName();
        apply(r, req);
        if (!oldName.equals(r.getName()) && rules.existsByVnfIdAndName(r.getVnfId(), r.getName())) {
            throw ApiException.conflict("Rule name already used on this VNF");
        }
        rules.findByVnfIdAndPriority(r.getVnfId(), r.getPriority())
                .filter(x -> !Objects.equals(x.getId(), r.getId()))
                .ifPresent(x -> {
                    throw ApiException.conflict("Priority " + r.getPriority() + " is already used by rule '" + x.getName() + "'");
                });
        rules.save(r);
        bump(v);
        audit.success(AuditAction.FW_RULE_UPDATED, "FIREWALL", "FIREWALL_RULE", r.getId(),
                "VNF " + v.getName() + ": '" + r.getName() + "' changed from [" + before + "] to [" + r.summary() + "]");
        return FirewallDtos.RuleDto.from(r);
    }

    @Transactional
    public FirewallDtos.RuleDto setEnabled(Long ruleId, boolean enabled) {
        FirewallRule r = rule(ruleId);
        Vnf v = editableVnf(r.getVnfId());
        if (r.isEnabled() != enabled) {
            r.setEnabled(enabled);
            rules.save(r);
            bump(v);
            audit.success(AuditAction.FW_RULE_UPDATED, "FIREWALL", "FIREWALL_RULE", r.getId(),
                    "VNF " + v.getName() + ": rule '" + r.getName() + "' " + (enabled ? "enabled" : "disabled"));
        }
        return FirewallDtos.RuleDto.from(r);
    }

    @Transactional
    public Long delete(Long ruleId) {
        FirewallRule r = rule(ruleId);
        Vnf v = editableVnf(r.getVnfId());
        rules.delete(r);
        bump(v);
        audit.success(AuditAction.FW_RULE_DELETED, "FIREWALL", "FIREWALL_RULE", ruleId,
                "VNF " + v.getName() + ": deleted '" + r.getName() + "' " + r.summary());
        return v.getId();
    }

    /** Re-assigns priorities 10, 20, 30 ... in the given order. The list must contain every rule of the VNF. */
    @Transactional
    public FirewallDtos.PolicyDto reorder(Long vnfId, List<Long> orderedIds) {
        Vnf v = editableVnf(vnfId);
        List<FirewallRule> current = rules.findByVnfIdOrderByPriorityAsc(vnfId);
        if (orderedIds.size() != current.size() || !new HashSet<>(orderedIds).equals(
                new HashSet<>(current.stream().map(FirewallRule::getId).toList()))) {
            throw ApiException.badRequest("ruleIds must list every rule of the VNF exactly once");
        }
        if (orderedIds.size() * 10 > 65535) throw ApiException.badRequest("Too many rules to renumber");
        // two-phase renumbering avoids transient unique-key collisions on (vnf_id, priority)
        for (int i = 0; i < current.size(); i++) current.get(i).setPriority(-(i + 1));
        rules.saveAllAndFlush(current);
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            FirewallRule r = current.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            r.setPriority((i + 1) * 10);
        }
        rules.saveAllAndFlush(current);
        bump(v);
        audit.success(AuditAction.FW_RULES_REORDERED, "FIREWALL", "VNF", vnfId,
                "VNF " + v.getName() + ": new evaluation order " + orderedIds);
        return policy(vnfId);
    }

    /** Live view of what is actually loaded in the VNF (from the agent / nftables). */
    public AgentModels.CurrentRuleset runtime(Long vnfId) {
        Vnf v = vnfs.findById(vnfId).orElseThrow(() -> ApiException.notFound("VNF", vnfId));
        if (v.getState() != VnfState.ACTIVE) {
            throw ApiException.invalidState("Runtime ruleset is available only for ACTIVE VNFs");
        }
        return agent.currentRuleset(v.getAgentUrl());
    }

    // ------------------------------------------------------------------ helpers

    /** Validates and normalises a rule request onto the entity. */
    static void apply(FirewallRule r, FirewallDtos.RuleRequest req) {
        r.setName(req.name().trim());
        r.setPriority(req.priority());
        r.setAction(req.action());
        r.setProtocol(req.protocol());
        r.setSourceCidr(Ipv4.normalizeCidr(req.sourceCidr(), "sourceCidr"));
        r.setDestinationCidr(Ipv4.normalizeCidr(req.destinationCidr(), "destinationCidr"));
        String sp = Ipv4.normalizePortSpec(req.sourcePort(), "sourcePort");
        String dp = Ipv4.normalizePortSpec(req.destinationPort(), "destinationPort");
        boolean portCapable = req.protocol() == FirewallRule.Protocol.TCP || req.protocol() == FirewallRule.Protocol.UDP;
        if ((sp != null || dp != null) && !portCapable) {
            throw ApiException.badRequest("Ports can only be specified for TCP or UDP rules");
        }
        r.setSourcePort(sp);
        r.setDestinationPort(dp);
        if (req.enabled() != null) r.setEnabled(req.enabled());
        if (req.logEnabled() != null) r.setLogEnabled(req.logEnabled());
        r.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
    }

    private void bump(Vnf v) {
        v.setRulesRevision(v.getRulesRevision() + 1);
        v.setSyncStatus(VnfEnums.SyncStatus.PENDING);
        vnfs.save(v);
    }

    private Vnf vnf(Long id) {
        return vnfs.findById(id).orElseThrow(() -> ApiException.notFound("VNF", id));
    }

    private Vnf editableVnf(Long id) {
        Vnf v = vnf(id);
        if (v.getVnfType() != VnfEnums.VnfType.FIREWALL) throw ApiException.badRequest("VNF is not a firewall");
        if (v.getState() == VnfState.TERMINATED || v.getState() == VnfState.TERMINATING) {
            throw ApiException.invalidState("Rules of a terminated VNF cannot be changed");
        }
        return v;
    }

    private FirewallRule rule(Long id) {
        return rules.findById(id).orElseThrow(() -> ApiException.notFound("Firewall rule", id));
    }
}
