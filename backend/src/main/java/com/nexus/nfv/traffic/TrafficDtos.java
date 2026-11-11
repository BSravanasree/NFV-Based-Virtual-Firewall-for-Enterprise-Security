package com.nexus.nfv.traffic;

import com.nexus.nfv.common.DataSource;

import java.time.Instant;
import java.util.List;

public final class TrafficDtos {

    private TrafficDtos() {
    }

    public record Summary(long windowMinutes, long samples, long allowedPackets, long allowedBytes,
                          long blockedPackets, long blockedBytes, long tcpPackets, long udpPackets, long icmpPackets,
                          long inboundBytes, long outboundBytes, long allowedConnectionEvents,
                          long blockedEvents, List<DataSource> dataSources) {
    }

    public record TimePoint(Instant t, long allowedPackets, long blockedPackets, long throughputBps,
                            long tcpPackets, long udpPackets, long icmpPackets, long inboundBytes,
                            long outboundBytes) {
    }

    public record KeyValue(String key, long count) {
    }

    public record TopTalkers(List<KeyValue> sources, List<KeyValue> blockedSources, List<KeyValue> destinations,
                             List<KeyValue> destinationPorts, List<KeyValue> protocols) {
    }

    public record EventQuery(Instant from, Instant to, Long vnfId, TrafficEvent.Action action, String protocol,
                             String srcIp, String dstIp, Integer port, String direction) {
    }

    public record EventDto(Long id, Long vnfId, Instant occurredAt, TrafficEvent.Action action, String protocol,
                           String srcIp, Integer srcPort, String dstIp, Integer dstPort, Long ruleId,
                           String direction, Integer packetLength, DataSource dataSource) {
        public static EventDto from(TrafficEvent e) {
            return new EventDto(e.getId(), e.getVnfId(), e.getOccurredAt(), e.getAction(), e.getProtocol(),
                    e.getSrcIp(), e.getSrcPort(), e.getDstIp(), e.getDstPort(), e.getRuleId(), e.getDirection(),
                    e.getPacketLength(), e.getDataSource());
        }
    }
}
