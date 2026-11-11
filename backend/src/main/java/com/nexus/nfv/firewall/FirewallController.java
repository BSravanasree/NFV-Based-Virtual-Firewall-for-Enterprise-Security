package com.nexus.nfv.firewall;

import com.nexus.nfv.firewall.agent.AgentModels;
import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Firewall policy API. Mutations persist the desired state first, then synchronously push it to the VNF; the
 * response always states whether the change is actually enforced ({@code sync.applied}).
 */
@RestController
@RequestMapping("/api/v1/firewall")
@Tag(name = "Firewall", description = "nftables firewall rules of firewall VNFs")
public class FirewallController {

    private final FirewallRuleService rules;
    private final FirewallSyncService sync;

    public FirewallController(FirewallRuleService rules, FirewallSyncService sync) {
        this.rules = rules;
        this.sync = sync;
    }

    @GetMapping("/vnfs/{vnfId}/policy")
    @PreAuthorize(Roles.ANY)
    @Operation(summary = "Desired policy (default action + ordered rules) with sync status")
    public FirewallDtos.PolicyDto policy(@PathVariable Long vnfId) {
        return rules.policy(vnfId);
    }

    @GetMapping("/vnfs/{vnfId}/runtime")
    @PreAuthorize(Roles.ANY)
    @Operation(summary = "Ruleset actually loaded in the VNF (nftables, via agent)")
    public AgentModels.CurrentRuleset runtime(@PathVariable Long vnfId) {
        return rules.runtime(vnfId);
    }

    @PostMapping("/vnfs/{vnfId}/rules")
    @PreAuthorize(Roles.OPERATE)
    @ResponseStatus(HttpStatus.CREATED)
    public FirewallDtos.RuleMutationResult create(@PathVariable Long vnfId,
                                                  @Valid @RequestBody FirewallDtos.RuleRequest req) {
        FirewallDtos.RuleDto r = rules.create(vnfId, req);
        return new FirewallDtos.RuleMutationResult(r, sync.applyIfActive(vnfId));
    }

    @GetMapping("/rules/{ruleId}")
    @PreAuthorize(Roles.ANY)
    public FirewallDtos.RuleDto get(@PathVariable Long ruleId) {
        return rules.get(ruleId);
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize(Roles.OPERATE)
    public FirewallDtos.RuleMutationResult update(@PathVariable Long ruleId,
                                                  @Valid @RequestBody FirewallDtos.RuleRequest req) {
        FirewallDtos.RuleDto r = rules.update(ruleId, req);
        return new FirewallDtos.RuleMutationResult(r, sync.applyIfActive(r.vnfId()));
    }

    @PatchMapping("/rules/{ruleId}/enabled")
    @PreAuthorize(Roles.OPERATE)
    public FirewallDtos.RuleMutationResult setEnabled(@PathVariable Long ruleId,
                                                      @Valid @RequestBody FirewallDtos.EnabledRequest req) {
        FirewallDtos.RuleDto r = rules.setEnabled(ruleId, req.enabled());
        return new FirewallDtos.RuleMutationResult(r, sync.applyIfActive(r.vnfId()));
    }

    @DeleteMapping("/rules/{ruleId}")
    @PreAuthorize(Roles.OPERATE)
    public FirewallDtos.SyncResult delete(@PathVariable Long ruleId) {
        Long vnfId = rules.delete(ruleId);
        return sync.applyIfActive(vnfId);
    }

    @PostMapping("/vnfs/{vnfId}/rules/reorder")
    @PreAuthorize(Roles.OPERATE)
    public FirewallDtos.PolicyDto reorder(@PathVariable Long vnfId, @Valid @RequestBody FirewallDtos.ReorderRequest req) {
        rules.reorder(vnfId, req.ruleIds());
        sync.applyIfActive(vnfId);
        return rules.policy(vnfId);
    }

    @PostMapping("/vnfs/{vnfId}/apply")
    @PreAuthorize(Roles.OPERATE)
    @Operation(summary = "Force re-application of the desired policy to the VNF")
    public FirewallDtos.SyncResult apply(@PathVariable Long vnfId) {
        return sync.apply(vnfId, true);
    }
}
