package com.nexus.nfv.traffic;

import com.nexus.nfv.common.net.Ipv4;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.firewall.FirewallRule;
import com.nexus.nfv.firewall.FirewallRuleRepository;
import com.nexus.nfv.firewall.agent.AgentModels;
import com.nexus.nfv.firewall.agent.FirewallAgentClient;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfRepository;
import com.nexus.nfv.vnf.VnfState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pulls telemetry from every ACTIVE firewall VNF:
 * <ul>
 *   <li>nftables counters → per-interval {@link TrafficSample} deltas + cumulative rule hit counters;</li>
 *   <li>NFLOG header events → {@link TrafficEvent} rows (input to threat detection).</li>
 * </ul>
 * Counters are exact kernel counters; events are per-packet header metadata (no payload inspection).
 */
@Component
public class TrafficCollector {

    private static final Logger log = LoggerFactory.getLogger(TrafficCollector.class);
    private static final int EVENT_BATCH = 2000;

    private final VnfRepository vnfs;
    private final FirewallRuleRepository rules;
    private final TrafficEventRepository events;
    private final TrafficSampleRepository samples;
    private final FirewallAgentClient agent;
    private final TransactionTemplate tx;
    private final String lanCidr;

    private final Map<Long, Snapshot> lastCounters = new ConcurrentHashMap<>();
    private final Map<Long, Cursor> cursors = new ConcurrentHashMap<>();

    record Snapshot(Instant at, Map<String, AgentModels.Counter> flat) {
    }

    record Cursor(String bootId, long nextSeq) {
    }

    public TrafficCollector(VnfRepository vnfs, FirewallRuleRepository rules, TrafficEventRepository events,
                            TrafficSampleRepository samples, FirewallAgentClient agent, PlatformTransactionManager txm,
                            NexusProperties props) {
        this.vnfs = vnfs;
        this.rules = rules;
        this.events = events;
        this.samples = samples;
        this.agent = agent;
        this.tx = new TransactionTemplate(txm);
        this.lanCidr = Ipv4.normalizeCidr(props.orchestrator().docker().lanCidr(), "lanCidr");
    }

    @Scheduled(fixedDelayString = "${nexus.monitoring.poll-interval-ms:10000}", initialDelay = 12000)
    public void collect() {
        for (Vnf v : vnfs.findByStateIn(EnumSet.of(VnfState.ACTIVE))) {
            if (v.getAgentUrl() == null) continue;
            try {
                collectCounters(v);
            } catch (RuntimeException ex) {
                log.debug("Counter collection from {} failed: {}", v.getName(), ex.getMessage());
            }
            try {
                collectEvents(v);
            } catch (RuntimeException ex) {
                log.debug("Event collection from {} failed: {}", v.getName(), ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ counters

    void collectCounters(Vnf v) {
        AgentModels.Counters c = agent.counters(v.getAgentUrl());
        Map<String, AgentModels.Counter> flat = flatten(c);
        Instant now = c.timestamp() != null ? c.timestamp() : Instant.now();
        Snapshot prev = lastCounters.put(v.getId(), new Snapshot(now, flat));
        if (prev == null) return; // first observation is the baseline – no fabricated deltas

        Map<String, AgentModels.Counter> delta = new HashMap<>();
        flat.forEach((k, cur) -> delta.put(k, diff(cur, prev.flat().get(k))));

        List<FirewallRule> vnfRules = rules.findByVnfIdOrderByPriorityAsc(v.getId());
        Map<String, FirewallRule> ruleById = vnfRules.stream()
                .collect(Collectors.toMap(r -> String.valueOf(r.getId()), Function.identity()));

        long allowedP = get(delta, "established").packets(), allowedB = get(delta, "established").bytes();
        long blockedP = get(delta, "defaultDrop").packets() + get(delta, "invalid").packets();
        long blockedB = get(delta, "defaultDrop").bytes() + get(delta, "invalid").bytes();
        for (Map.Entry<String, AgentModels.Counter> e : delta.entrySet()) {
            if (!e.getKey().startsWith("rule:")) continue;
            FirewallRule r = ruleById.get(e.getKey().substring(5));
            if (r == null) continue;
            if (r.getAction() == FirewallRule.Action.ALLOW) {
                allowedP += e.getValue().packets();
                allowedB += e.getValue().bytes();
            } else {
                blockedP += e.getValue().packets();
                blockedB += e.getValue().bytes();
            }
        }

        TrafficSample s = new TrafficSample();
        s.setVnfId(v.getId());
        s.setSampledAt(now);
        s.setIntervalMs(Math.max(1, now.toEpochMilli() - prev.at().toEpochMilli()));
        s.setAllowedPackets(allowedP);
        s.setAllowedBytes(allowedB);
        s.setBlockedPackets(blockedP);
        s.setBlockedBytes(blockedB);
        s.setTcpPackets(get(delta, "proto:tcp").packets());
        s.setUdpPackets(get(delta, "proto:udp").packets());
        s.setIcmpPackets(get(delta, "proto:icmp").packets());
        s.setInboundBytes(get(delta, "dir:inbound").bytes());
        s.setOutboundBytes(get(delta, "dir:outbound").bytes());
        s.setDataSource(v.dataSource());

        tx.executeWithoutResult(st -> {
            samples.save(s);
            for (FirewallRule r : vnfRules) {
                AgentModels.Counter d = delta.get("rule:" + r.getId());
                if (d == null || (d.packets() == 0 && d.bytes() == 0)) continue;
                rules.findById(r.getId()).ifPresent(fresh -> {
                    fresh.setHitPackets(fresh.getHitPackets() + d.packets());
                    fresh.setHitBytes(fresh.getHitBytes() + d.bytes());
                    fresh.setCountersUpdatedAt(now);
                    rules.save(fresh);
                });
            }
        });
    }

    static Map<String, AgentModels.Counter> flatten(AgentModels.Counters c) {
        Map<String, AgentModels.Counter> m = new HashMap<>();
        if (c.rules() != null) c.rules().forEach((k, v) -> m.put("rule:" + k, v));
        if (c.established() != null) m.put("established", c.established());
        if (c.defaultDrop() != null) m.put("defaultDrop", c.defaultDrop());
        if (c.invalid() != null) m.put("invalid", c.invalid());
        if (c.protocols() != null) c.protocols().forEach((k, v) -> m.put("proto:" + k, v));
        if (c.direction() != null) c.direction().forEach((k, v) -> m.put("dir:" + k, v));
        return m;
    }

    /** Counter delta; a decrease means the nftables table was re-created (policy apply / restart). */
    static AgentModels.Counter diff(AgentModels.Counter cur, AgentModels.Counter prev) {
        if (prev == null || cur.packets() < prev.packets() || cur.bytes() < prev.bytes()) return cur;
        return new AgentModels.Counter(cur.packets() - prev.packets(), cur.bytes() - prev.bytes());
    }

    private static AgentModels.Counter get(Map<String, AgentModels.Counter> m, String k) {
        return m.getOrDefault(k, AgentModels.Counter.ZERO);
    }

    // ------------------------------------------------------------------ events

    void collectEvents(Vnf v) {
        Cursor cur = cursors.computeIfAbsent(v.getId(), id -> new Cursor(null, events.maxAgentSeq(id)));
        AgentModels.Events res = agent.events(v.getAgentUrl(), cur.nextSeq(), EVENT_BATCH);
        if (!res.available()) return;
        boolean agentRestarted = (cur.bootId() != null && !cur.bootId().equals(res.bootId()))
                || res.nextSeq() < cur.nextSeq();
        if (agentRestarted) {
            // agent (or VNF) restarted: its sequence restarted at 0
            cursors.put(v.getId(), new Cursor(res.bootId(), 0));
            res = agent.events(v.getAgentUrl(), 0, EVENT_BATCH);
        }
        List<TrafficEvent> batch = new ArrayList<>(res.events().size());
        java.util.Set<Long> knownRules = rules.findByVnfIdOrderByPriorityAsc(v.getId()).stream()
                .map(FirewallRule::getId).collect(Collectors.toSet());
        for (AgentModels.Event e : res.events()) {
            if (!Ipv4.isAddress(e.srcIp()) || !Ipv4.isAddress(e.dstIp())) continue; // IPv4 only
            TrafficEvent t = new TrafficEvent();
            t.setVnfId(v.getId());
            t.setOccurredAt(e.ts() == null ? Instant.now() : e.ts());
            t.setAction("ALLOW".equalsIgnoreCase(e.action()) ? TrafficEvent.Action.ALLOWED : TrafficEvent.Action.BLOCKED);
            t.setProtocol(e.protocol() == null ? "OTHER" : e.protocol().toUpperCase());
            t.setSrcIp(e.srcIp());
            t.setDstIp(e.dstIp());
            t.setSrcPort(e.srcPort());
            t.setDstPort(e.dstPort());
            t.setRuleId(e.ruleId() != null && knownRules.contains(e.ruleId()) ? e.ruleId() : null);
            t.setDirection(lanCidr != null && Ipv4.contains(lanCidr, e.srcIp()) ? "OUTBOUND" : "INBOUND");
            t.setPacketLength(e.length());
            t.setDataSource(v.dataSource());
            t.setAgentSeq(e.seq());
            batch.add(t);
        }
        if (!batch.isEmpty()) {
            tx.executeWithoutResult(st -> events.saveAll(batch));
        }
        cursors.put(v.getId(), new Cursor(res.bootId(), res.nextSeq()));
    }
}
