package com.nexus.nfv.firewall;

import com.nexus.nfv.vnf.VnfEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class FirewallDtos {

    private FirewallDtos() {
    }

    public record RuleRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9 _.\\-]+$",
                    message = "letters, digits, space, '_', '.', '-' only") String name,
            @NotNull @Min(1) @Max(65535) Integer priority,
            @NotNull FirewallRule.Action action,
            @NotNull FirewallRule.Protocol protocol,
            @Size(max = 43) String sourceCidr,
            @Size(max = 43) String destinationCidr,
            @Size(max = 11) String sourcePort,
            @Size(max = 11) String destinationPort,
            Boolean enabled,
            Boolean logEnabled,
            @Size(max = 255) String description) {
    }

    public record EnabledRequest(@NotNull Boolean enabled) {
    }

    public record ReorderRequest(@NotEmpty List<Long> ruleIds) {
    }

    public record RuleDto(Long id, Long vnfId, String name, int priority, FirewallRule.Action action,
                          FirewallRule.Protocol protocol, String sourceCidr, String destinationCidr,
                          String sourcePort, String destinationPort, boolean enabled, boolean logEnabled,
                          String description, long hitPackets, long hitBytes, Instant countersUpdatedAt,
                          String createdBy, Instant createdAt, Instant updatedAt) {
        public static RuleDto from(FirewallRule r) {
            return new RuleDto(r.getId(), r.getVnfId(), r.getName(), r.getPriority(), r.getAction(), r.getProtocol(),
                    r.getSourceCidr(), r.getDestinationCidr(), r.getSourcePort(), r.getDestinationPort(),
                    r.isEnabled(), r.isLogEnabled(), r.getDescription(), r.getHitPackets(), r.getHitBytes(),
                    r.getCountersUpdatedAt(), r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    /** Result of pushing the desired policy to the VNF. applied=false always carries the reason. */
    public record SyncResult(boolean attempted, boolean applied, VnfEnums.SyncStatus status, long desiredRevision,
                             long appliedRevision, String message) {
    }

    public record RuleMutationResult(RuleDto rule, SyncResult sync) {
    }

    public record PolicyDto(Long vnfId, String vnfName, String vnfState, VnfEnums.DefaultAction defaultAction,
                            long rulesRevision, long appliedRevision, Instant rulesAppliedAt,
                            VnfEnums.SyncStatus syncStatus, List<RuleDto> rules) {
    }
}
