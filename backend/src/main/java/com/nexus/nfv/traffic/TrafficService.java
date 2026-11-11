package com.nexus.nfv.traffic;

import com.nexus.nfv.common.DataSource;
import com.nexus.nfv.common.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class TrafficService {

    private final TrafficSampleRepository samples;
    private final TrafficEventRepository events;

    public TrafficService(TrafficSampleRepository samples, TrafficEventRepository events) {
        this.samples = samples;
        this.events = events;
    }

    public TrafficDtos.Summary summary(Duration window, Long vnfId) {
        Instant since = Instant.now().minus(window);
        var t = samples.totals(since, vnfId);
        List<TrafficSample> list = vnfId == null ? samples.findBySampledAtAfterOrderBySampledAtAsc(since)
                : samples.findByVnfIdAndSampledAtAfterOrderBySampledAtAsc(vnfId, since);
        Set<DataSource> sources = EnumSet.noneOf(DataSource.class);
        list.forEach(s -> sources.add(s.getDataSource()));
        long blockedEvents = events.countByOccurredAtAfterAndAction(since, TrafficEvent.Action.BLOCKED);
        long allowedEvents = events.countByOccurredAtAfterAndAction(since, TrafficEvent.Action.ALLOWED);
        return new TrafficDtos.Summary(window.toMinutes(), t.getSamples(), t.getAllowedPackets(), t.getAllowedBytes(),
                t.getBlockedPackets(), t.getBlockedBytes(), t.getTcpPackets(), t.getUdpPackets(), t.getIcmpPackets(),
                t.getInboundBytes(), t.getOutboundBytes(), allowedEvents, blockedEvents, List.copyOf(sources));
    }

    /** Aggregates counter samples into fixed buckets. Buckets with no samples are omitted, never zero-filled. */
    public List<TrafficDtos.TimePoint> timeseries(Duration window, int bucketSeconds, Long vnfId) {
        Instant since = Instant.now().minus(window);
        List<TrafficSample> list = vnfId == null ? samples.findBySampledAtAfterOrderBySampledAtAsc(since)
                : samples.findByVnfIdAndSampledAtAfterOrderBySampledAtAsc(vnfId, since);
        long bucketMs = Math.max(10, bucketSeconds) * 1000L;
        TreeMap<Long, long[]> buckets = new TreeMap<>();
        for (TrafficSample s : list) {
            long key = (s.getSampledAt().toEpochMilli() / bucketMs) * bucketMs;
            long[] b = buckets.computeIfAbsent(key, k -> new long[9]);
            b[0] += s.getAllowedPackets();
            b[1] += s.getBlockedPackets();
            b[2] += s.getAllowedBytes() + s.getBlockedBytes();
            b[3] += s.getTcpPackets();
            b[4] += s.getUdpPackets();
            b[5] += s.getIcmpPackets();
            b[6] += s.getInboundBytes();
            b[7] += s.getOutboundBytes();
            b[8] += s.getIntervalMs();
        }
        List<TrafficDtos.TimePoint> out = new ArrayList<>(buckets.size());
        buckets.forEach((k, b) -> out.add(new TrafficDtos.TimePoint(Instant.ofEpochMilli(k), b[0], b[1],
                (b[6] + b[7]) * 8_000 / bucketMs, b[3], b[4], b[5], b[6], b[7])));
        return out;
    }

    public TrafficDtos.TopTalkers top(Duration window, int limit) {
        Instant since = Instant.now().minus(window);
        PageRequest page = PageRequest.of(0, Math.min(Math.max(limit, 1), 50));
        return new TrafficDtos.TopTalkers(
                map(events.topSources(since, null, page)),
                map(events.topSources(since, TrafficEvent.Action.BLOCKED, page)),
                map(events.topDestinations(since, page)),
                map(events.topDestinationPorts(since, page)),
                map(events.protocolDistribution(since)));
    }

    public PageResponse<TrafficDtos.EventDto> searchEvents(TrafficDtos.EventQuery q, int page, int size) {
        return PageResponse.of(events.findAll(spec(q), PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 500),
                Sort.by(Sort.Direction.DESC, "occurredAt"))), TrafficDtos.EventDto::from);
    }

    public List<TrafficDtos.EventDto> exportEvents(TrafficDtos.EventQuery q, int limit) {
        return events.findAll(spec(q), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "occurredAt")))
                .map(TrafficDtos.EventDto::from).getContent();
    }

    static Specification<TrafficEvent> spec(TrafficDtos.EventQuery q) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q.from() != null) p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), q.from()));
            if (q.to() != null) p.add(cb.lessThanOrEqualTo(root.get("occurredAt"), q.to()));
            if (q.vnfId() != null) p.add(cb.equal(root.get("vnfId"), q.vnfId()));
            if (q.action() != null) p.add(cb.equal(root.get("action"), q.action()));
            if (q.protocol() != null && !q.protocol().isBlank()) p.add(cb.equal(root.get("protocol"), q.protocol().toUpperCase()));
            if (q.srcIp() != null && !q.srcIp().isBlank()) p.add(cb.equal(root.get("srcIp"), q.srcIp().trim()));
            if (q.dstIp() != null && !q.dstIp().isBlank()) p.add(cb.equal(root.get("dstIp"), q.dstIp().trim()));
            if (q.port() != null) p.add(cb.or(cb.equal(root.get("dstPort"), q.port()), cb.equal(root.get("srcPort"), q.port())));
            if (q.direction() != null && !q.direction().isBlank()) p.add(cb.equal(root.get("direction"), q.direction().toUpperCase()));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static List<TrafficDtos.KeyValue> map(List<TrafficEventRepository.KeyCount> l) {
        return l.stream().map(k -> new TrafficDtos.KeyValue(k.getKey(), k.getTotal())).toList();
    }
}
