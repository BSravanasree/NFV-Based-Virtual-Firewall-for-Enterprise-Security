package com.nexus.nfv.vnf;

import com.nexus.nfv.common.DataSource;
import com.nexus.nfv.vnf.orchestrator.VnfOrchestrator;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class VnfDtos {

    private VnfDtos() {
    }

    static final String IPV4 = "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$";

    public record CreateVnfRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9-]{1,30}[a-z0-9]$",
                    message = "3-32 chars: lower-case letters, digits and '-', starting with a letter") String name,
            @NotNull VnfEnums.VnfType vnfType,
            @Size(max = 500) String description,
            @Pattern(regexp = IPV4, message = "must be an IPv4 address") String lanIp,
            @Pattern(regexp = IPV4, message = "must be an IPv4 address") String wanIp,
            @NotNull @DecimalMin("0.25") @DecimalMax("4.0") BigDecimal cpuLimit,
            @NotNull @Min(128) @Max(4096) Integer memoryLimitMb,
            VnfEnums.DefaultAction defaultAction) {
    }

    public record ConfigureVnfRequest(
            @Size(max = 500) String description,
            VnfEnums.DefaultAction defaultAction) {
    }

    public record ScaleVnfRequest(
            @NotNull VnfOrchestrator.ScaleDirection direction,
            @DecimalMin("0.25") @DecimalMax("4.0") BigDecimal cpuLimit,
            @Min(128) @Max(4096) Integer memoryLimitMb) {
    }

    public record VnfDto(
            Long id, String vnfId, String name, VnfEnums.VnfType vnfType, String description,
            VnfEnums.OrchestratorType orchestrator, VnfState state, VnfEnums.Health health,
            String externalId, String mgmtIp, String lanIp, String wanIp,
            BigDecimal cpuLimit, int memoryLimitMb, VnfEnums.DefaultAction defaultAction,
            long rulesRevision, long appliedRevision, Instant rulesAppliedAt, VnfEnums.SyncStatus syncStatus,
            BigDecimal cpuPercent, BigDecimal memoryPercent, BigDecimal memoryUsageMb,
            Long rxBytes, Long txBytes, Long throughputBps, DataSource metricsSource, Instant metricsUpdatedAt,
            String lastError, String createdBy, Instant createdAt, Instant updatedAt) {

        public static VnfDto from(Vnf v) {
            return new VnfDto(v.getId(), String.format("VNF-%05d", v.getId()), v.getName(), v.getVnfType(),
                    v.getDescription(), v.getOrchestrator(), v.getState(), v.getHealth(), v.getExternalId(),
                    v.getMgmtIp(), v.getLanIp(), v.getWanIp(), v.getCpuLimit(), v.getMemoryLimitMb(),
                    v.getDefaultAction(), v.getRulesRevision(), v.getAppliedRevision(), v.getRulesAppliedAt(),
                    v.getSyncStatus(), v.getCpuPercent(), v.getMemoryPercent(), v.getMemoryUsageMb(),
                    v.getRxBytes(), v.getTxBytes(), v.getThroughputBps(), v.getMetricsSource(),
                    v.getMetricsUpdatedAt(), v.getLastError(), v.getCreatedBy(), v.getCreatedAt(), v.getUpdatedAt());
        }
    }

    public record LifecycleEventDto(Long id, Long vnfId, VnfEnums.Operation operation, VnfState fromState,
                                    VnfState toState, String outcome, String message, String actor,
                                    Instant occurredAt) {
        public static LifecycleEventDto from(VnfLifecycleEvent e) {
            return new LifecycleEventDto(e.getId(), e.getVnfId(), e.getOperation(), e.getFromState(), e.getToState(),
                    e.getOutcome(), e.getMessage(), e.getActor(), e.getOccurredAt());
        }
    }

    public record MetricSampleDto(Instant sampledAt, BigDecimal cpuPercent, BigDecimal memoryPercent,
                                  BigDecimal memoryUsageMb, Long throughputBps, DataSource dataSource) {
        public static MetricSampleDto from(VnfMetricSample s) {
            return new MetricSampleDto(s.getSampledAt(), s.getCpuPercent(), s.getMemoryPercent(),
                    s.getMemoryUsageMb(), s.getThroughputBps(), s.getDataSource());
        }
    }
}
