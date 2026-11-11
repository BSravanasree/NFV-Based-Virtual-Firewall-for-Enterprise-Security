package com.nexus.nfv.netconf;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class NetconfDtos {

    private NetconfDtos() {
    }

    public record DeviceRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String name,
            @NotBlank @Size(max = 255) @Pattern(regexp = "^[A-Za-z0-9.-]+$", message = "hostname or IPv4 address") String host,
            @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
            @NotBlank @Size(max = 128) String password,
            Long vnfId) {
    }

    /** Credentials are never part of a response. */
    public record DeviceDto(Long id, String name, String host, int port, String username, Long vnfId, String status,
                            List<String> capabilities, String hostKeyFingerprint, String lastError,
                            Instant lastConnectedAt, String createdBy, Instant createdAt) {
    }

    public record VersionDto(Long id, Long deviceId, int versionNo, String operation, String datastore,
                             String checksum, String status, String message, int ruleCount, String createdBy,
                             Instant createdAt) {
        public static VersionDto from(NetconfConfigVersion v) {
            return new VersionDto(v.getId(), v.getDeviceId(), v.getVersionNo(), v.getOperation(), v.getDatastore(),
                    v.getChecksum(), v.getStatus(), v.getMessage(), FirewallYangCodec.ruleCount(v.getConfigXml()),
                    v.getCreatedBy(), v.getCreatedAt());
        }
    }

    public record VersionContent(VersionDto version, String configXml) {
    }

    public record RunningConfig(Long deviceId, String datastore, String configXml, String checksum, int ruleCount,
                                Instant retrievedAt) {
    }

    /** IN_SYNC: running == desired; DRIFT: differs; UNKNOWN: device unreachable. */
    public record SyncReport(Long deviceId, String status, String runningChecksum, String desiredChecksum,
                             String lastAppliedChecksum, Integer lastAppliedVersion, String detail) {
    }

    public record OperationResult(boolean success, String message, VersionDto version) {
    }
}
