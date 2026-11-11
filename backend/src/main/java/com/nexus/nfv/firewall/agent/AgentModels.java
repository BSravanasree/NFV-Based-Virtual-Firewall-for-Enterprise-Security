package com.nexus.nfv.firewall.agent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Wire contract of the firewall VNF agent (vnf-firewall/agent/nexus_agent.py).
 * Documented in docs/FIREWALL_IMPLEMENTATION.md.
 */
public final class AgentModels {

    private AgentModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Health(String status, String hostname, String bootId, long appliedRevision, String rulesSource,
                         boolean nftables, boolean netconf, boolean eventLogging,
                         Map<String, String> interfaces, long uptimeSeconds, String agentVersion) {
    }

    public record Rule(long id, int priority, String action, String protocol, String srcCidr, String dstCidr,
                       String srcPort, String dstPort, boolean log) {
    }

    public record Ruleset(long revision, String defaultAction, List<Rule> rules) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApplyResult(long appliedRevision, int ruleCount, Instant appliedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurrentRuleset(long revision, String defaultAction, List<Rule> rules, String source,
                                 String rendered) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Counter(long packets, long bytes) {
        public static final Counter ZERO = new Counter(0, 0);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Counters(Instant timestamp, long appliedRevision, Map<String, Counter> rules,
                           Counter established, Counter defaultDrop, Counter invalid,
                           Map<String, Counter> protocols, Map<String, Counter> direction) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(long seq, Instant ts, String action, Long ruleId, String protocol, String srcIp,
                        String dstIp, Integer srcPort, Integer dstPort, String inIface, Integer length) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Events(String bootId, long nextSeq, boolean available, List<Event> events) {
    }
}
