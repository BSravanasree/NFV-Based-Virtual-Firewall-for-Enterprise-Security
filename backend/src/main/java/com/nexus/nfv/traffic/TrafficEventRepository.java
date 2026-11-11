package com.nexus.nfv.traffic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TrafficEventRepository extends JpaRepository<TrafficEvent, Long>, JpaSpecificationExecutor<TrafficEvent> {

    @Query("select coalesce(max(e.agentSeq), 0) from TrafficEvent e where e.vnfId = :vnfId")
    long maxAgentSeq(@Param("vnfId") Long vnfId);

    long countByOccurredAtAfterAndAction(Instant after, TrafficEvent.Action action);

    // ---- aggregations used by monitoring and threat detection (all bounded by a time window)

    @Query("""
            select e.srcIp as key, count(e) as total from TrafficEvent e
            where e.occurredAt >= :since and (:action is null or e.action = :action)
            group by e.srcIp order by count(e) desc""")
    List<KeyCount> topSources(@Param("since") Instant since, @Param("action") TrafficEvent.Action action,
                              org.springframework.data.domain.Pageable page);

    @Query("""
            select e.dstIp as key, count(e) as total from TrafficEvent e
            where e.occurredAt >= :since group by e.dstIp order by count(e) desc""")
    List<KeyCount> topDestinations(@Param("since") Instant since, org.springframework.data.domain.Pageable page);

    @Query("""
            select concat('', e.dstPort) as key, count(e) as total from TrafficEvent e
            where e.occurredAt >= :since and e.dstPort is not null
            group by e.dstPort order by count(e) desc""")
    List<KeyCount> topDestinationPorts(@Param("since") Instant since, org.springframework.data.domain.Pageable page);

    @Query("""
            select e.protocol as key, count(e) as total from TrafficEvent e
            where e.occurredAt >= :since group by e.protocol""")
    List<KeyCount> protocolDistribution(@Param("since") Instant since);

    @Query("""
            select e.srcIp as srcIp, count(distinct e.dstPort) as distinctPorts, count(e) as total,
                   min(e.occurredAt) as firstSeen, max(e.occurredAt) as lastSeen, max(e.vnfId) as vnfId,
                   max(e.dstIp) as sampleDst
            from TrafficEvent e
            where e.occurredAt >= :since and (:action is null or e.action = :action)
            group by e.srcIp""")
    List<SourceStats> sourceStats(@Param("since") Instant since, @Param("action") TrafficEvent.Action action);

    @Query("""
            select e.srcIp as srcIp, count(distinct e.dstPort) as distinctPorts, count(e) as total,
                   min(e.occurredAt) as firstSeen, max(e.occurredAt) as lastSeen, max(e.vnfId) as vnfId,
                   max(e.dstIp) as sampleDst
            from TrafficEvent e
            where e.occurredAt >= :since and e.action = com.nexus.nfv.traffic.TrafficEvent.Action.BLOCKED
              and e.dstPort in :ports
            group by e.srcIp""")
    List<SourceStats> blockedToPorts(@Param("since") Instant since, @Param("ports") List<Integer> ports);

    @Query("""
            select e.srcIp as srcIp, count(distinct e.dstIp) as distinctPorts, count(e) as total,
                   min(e.occurredAt) as firstSeen, max(e.occurredAt) as lastSeen, max(e.vnfId) as vnfId,
                   max(e.dstIp) as sampleDst
            from TrafficEvent e
            where e.occurredAt >= :since and e.action = com.nexus.nfv.traffic.TrafficEvent.Action.BLOCKED
              and e.ruleId is null
            group by e.srcIp""")
    List<SourceStats> defaultPolicyDrops(@Param("since") Instant since);

    @Modifying
    @Query("delete from TrafficEvent e where e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    interface KeyCount {
        String getKey();

        long getTotal();
    }

    interface SourceStats {
        String getSrcIp();

        long getDistinctPorts();

        long getTotal();

        Instant getFirstSeen();

        Instant getLastSeen();

        Long getVnfId();

        String getSampleDst();
    }
}
