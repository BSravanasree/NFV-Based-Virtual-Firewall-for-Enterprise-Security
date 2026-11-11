package com.nexus.nfv.traffic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TrafficSampleRepository extends JpaRepository<TrafficSample, Long> {

    List<TrafficSample> findBySampledAtAfterOrderBySampledAtAsc(Instant after);

    List<TrafficSample> findByVnfIdAndSampledAtAfterOrderBySampledAtAsc(Long vnfId, Instant after);

    @Query("""
            select coalesce(sum(s.allowedPackets),0) as allowedPackets, coalesce(sum(s.allowedBytes),0) as allowedBytes,
                   coalesce(sum(s.blockedPackets),0) as blockedPackets, coalesce(sum(s.blockedBytes),0) as blockedBytes,
                   coalesce(sum(s.tcpPackets),0) as tcpPackets, coalesce(sum(s.udpPackets),0) as udpPackets,
                   coalesce(sum(s.icmpPackets),0) as icmpPackets, coalesce(sum(s.inboundBytes),0) as inboundBytes,
                   coalesce(sum(s.outboundBytes),0) as outboundBytes, count(s) as samples
            from TrafficSample s where s.sampledAt >= :since and (:vnfId is null or s.vnfId = :vnfId)""")
    Totals totals(@Param("since") Instant since, @Param("vnfId") Long vnfId);

    @Modifying
    @Query("delete from TrafficSample s where s.sampledAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    interface Totals {
        long getAllowedPackets();

        long getAllowedBytes();

        long getBlockedPackets();

        long getBlockedBytes();

        long getTcpPackets();

        long getUdpPackets();

        long getIcmpPackets();

        long getInboundBytes();

        long getOutboundBytes();

        long getSamples();
    }
}
