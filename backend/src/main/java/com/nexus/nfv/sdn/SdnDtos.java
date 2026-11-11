package com.nexus.nfv.sdn;

import com.nexus.nfv.common.DataSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class SdnDtos {

    private SdnDtos() {
    }

    public record ControllerStatus(String mode, DataSource dataSource, String endpoint, boolean reachable,
                                   long latencyMs, String version, String nodeId, int devices, int hosts, int links,
                                   int flows, List<String> activeApps, String error) {
    }

    public record DeviceDto(String id, String name, String type, boolean available, String manufacturer,
                            String hwVersion, String swVersion, String protocol, String managementAddress,
                            String channelId, String role, DataSource dataSource) {
    }

    public record PortDto(String port, String name, boolean enabled, String type, long speedMbps, String mac,
                          Long packetsReceived, Long packetsSent, Long bytesReceived, Long bytesSent,
                          Long rxDropped, Long txDropped) {
    }

    public record HostDto(String id, String name, String mac, String vlan, List<String> ipAddresses,
                          String deviceId, String port, DataSource dataSource) {
    }

    public record LinkDto(String srcDevice, String srcPort, String dstDevice, String dstPort, String type,
                          String state, DataSource dataSource) {
    }

    public record FlowDto(String id, String deviceId, String appId, int priority, int tableId, String state,
                          long packets, long bytes, long lifeSeconds, boolean permanent, String match,
                          String actions, boolean managedByNexus, DataSource dataSource) {
    }

    public record PolicyRequest(
            @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9 _.\\-]+$") String name,
            @Size(max = 255) String description,
            @NotBlank @Size(max = 128) @Pattern(regexp = "^of:[0-9a-fA-F]{16}$", message = "must be an OpenFlow device id (of:xxxxxxxxxxxxxxxx)") String deviceId,
            @NotNull @Min(1000) @Max(60000) Integer priority,
            @NotBlank @Pattern(regexp = "^(ANY|TCP|UDP|ICMP)$") String ipProtocol,
            @Size(max = 43) String sourceCidr,
            @Size(max = 43) String destinationCidr,
            @Min(1) @Max(65535) Integer destinationPort,
            @NotNull NetworkPolicy.Action action,
            @Size(max = 8) String outputPort) {
    }

    public record PolicyDto(Long id, String name, String description, String deviceId, int priority,
                            String ipProtocol, String sourceCidr, String destinationCidr, Integer destinationPort,
                            NetworkPolicy.Action action, String outputPort, String onosFlowId,
                            NetworkPolicy.Status status, DataSource dataSource, String lastError, String createdBy,
                            Instant createdAt) {
        public static PolicyDto from(NetworkPolicy p) {
            return new PolicyDto(p.getId(), p.getName(), p.getDescription(), p.getDeviceId(), p.getPriority(),
                    p.getIpProtocol(), p.getSourceCidr(), p.getDestinationCidr(), p.getDestinationPort(), p.getAction(),
                    p.getOutputPort(), p.getOnosFlowId(), p.getStatus(), p.getDataSource(), p.getLastError(),
                    p.getCreatedBy(), p.getCreatedAt());
        }
    }

    // ---- topology graph

    public record Node(String id, String type, String label, String detail, String status, DataSource dataSource) {
    }

    public record Edge(String id, String source, String target, String type, String label) {
    }

    public record Topology(String view, DataSource dataSource, boolean available, String message,
                           List<Node> nodes, List<Edge> edges) {
    }
}
