package com.nexus.nfv.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nfv.common.Csv;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.common.net.Ipv4;
import com.nexus.nfv.firewall.FirewallRule;
import com.nexus.nfv.firewall.FirewallSyncService;
import com.nexus.nfv.firewall.agent.AgentModels;
import com.nexus.nfv.netconf.FirewallYangCodec;
import com.nexus.nfv.sdn.FlowTranslator;
import com.nexus.nfv.sdn.NetworkPolicy;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import com.nexus.nfv.vnf.VnfState;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainLogicTest {

    @Nested
    class VnfStateMachine {
        @Test
        void happyPathTransitionsAreAllowed() {
            assertThat(VnfState.CREATED.canTransitionTo(VnfState.INSTANTIATING)).isTrue();
            assertThat(VnfState.INSTANTIATING.canTransitionTo(VnfState.ACTIVE)).isTrue();
            assertThat(VnfState.ACTIVE.canTransitionTo(VnfState.STOPPING)).isTrue();
            assertThat(VnfState.STOPPING.canTransitionTo(VnfState.STOPPED)).isTrue();
            assertThat(VnfState.STOPPED.canTransitionTo(VnfState.TERMINATING)).isTrue();
            assertThat(VnfState.TERMINATING.canTransitionTo(VnfState.TERMINATED)).isTrue();
        }

        @Test
        void illegalTransitionsAreRejected() {
            assertThat(VnfState.CREATED.canTransitionTo(VnfState.ACTIVE)).isFalse();
            assertThat(VnfState.TERMINATED.canTransitionTo(VnfState.INSTANTIATING)).isFalse();
            assertThat(VnfState.STOPPED.canTransitionTo(VnfState.ACTIVE)).isFalse();
            assertThat(VnfState.INSTANTIATING.canTransitionTo(VnfState.STOPPING)).isFalse();
        }

        @Test
        void failureIsReachableFromTransitionalStatesAndRecoverable() {
            for (VnfState s : VnfState.values()) {
                if (s.isTransitional() && s != VnfState.TERMINATING) {
                    assertThat(s.canTransitionTo(VnfState.FAILED)).as(s.name()).isTrue();
                }
            }
            assertThat(VnfState.FAILED.canTransitionTo(VnfState.INSTANTIATING)).isTrue();
            assertThat(VnfState.FAILED.canTransitionTo(VnfState.TERMINATING)).isTrue();
        }
    }

    @Nested
    class Ipv4Validation {
        @ParameterizedTest
        @CsvSource({"10.0.0.5,10.0.0.5/32", "10.10.0.0/24,10.10.0.0/24", "0.0.0.0/0,0.0.0.0/0", "any,", "' ',"})
        void normalisesCidrs(String in, String expected) {
            assertThat(Ipv4.normalizeCidr(in, "f")).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"10.0.0.1/24", "256.1.1.1", "10.0.0.0/33", "10.0.0.0/8; flush ruleset", "example.com", "::1"})
        void rejectsInvalidOrAmbiguousCidrs(String in) {
            assertThatThrownBy(() -> Ipv4.normalizeCidr(in, "f")).isInstanceOf(ApiException.class);
        }

        @ParameterizedTest
        @CsvSource({"80,80", "1000-2000,1000-2000", "443-443,443"})
        void normalisesPorts(String in, String expected) {
            assertThat(Ipv4.normalizePortSpec(in, "p")).isEqualTo(expected);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "65536", "2000-1000", "80,443", "http", "1-70000"})
        void rejectsInvalidPorts(String in) {
            assertThatThrownBy(() -> Ipv4.normalizePortSpec(in, "p")).isInstanceOf(ApiException.class);
        }

        @Test
        void containment() {
            assertThat(Ipv4.contains("10.10.0.0/24", "10.10.0.11")).isTrue();
            assertThat(Ipv4.contains("10.10.0.0/24", "10.20.0.10")).isFalse();
        }
    }

    @Nested
    class OpenFlowTranslation {
        private final ObjectMapper mapper = new ObjectMapper();

        private NetworkPolicy policy(NetworkPolicy.Action action, String proto, Integer port, String out) {
            NetworkPolicy p = new NetworkPolicy();
            p.setDeviceId("of:0000000000000001");
            p.setPriority(40001);
            p.setIpProtocol(proto);
            p.setSourceCidr("10.0.1.11/32");
            p.setDestinationCidr("10.0.2.0/24");
            p.setDestinationPort(port);
            p.setAction(action);
            p.setOutputPort(out);
            return p;
        }

        @Test
        void dropPolicyCompilesToNoActionWithFullMatch() {
            ObjectNode f = FlowTranslator.toOnosFlow(policy(NetworkPolicy.Action.DROP, "TCP", 22, null), mapper);
            JsonNode c = f.path("selector").path("criteria");
            assertThat(c).hasSize(5);
            assertThat(c.get(0).path("ethType").asText()).isEqualTo("0x0800");
            assertThat(c.get(1).path("protocol").asInt()).isEqualTo(6);
            assertThat(c.get(4).path("type").asText()).isEqualTo("TCP_DST");
            assertThat(c.get(4).path("tcpPort").asInt()).isEqualTo(22);
            assertThat(f.path("treatment").path("instructions").get(0).path("type").asText()).isEqualTo("NOACTION");
            assertThat(f.path("isPermanent").asBoolean()).isTrue();
        }

        @Test
        void steeringRequiresNumericOutputPort() {
            ObjectNode f = FlowTranslator.toOnosFlow(policy(NetworkPolicy.Action.STEER_TO_FIREWALL, "ANY", null, "3"), mapper);
            assertThat(f.path("treatment").path("instructions").get(0).path("port").asText()).isEqualTo("3");
            assertThatThrownBy(() -> FlowTranslator.toOnosFlow(policy(NetworkPolicy.Action.FORWARD, "ANY", null, null), mapper))
                    .isInstanceOf(ApiException.class);
        }

        @Test
        void portWithoutTransportProtocolIsRejected() {
            assertThatThrownBy(() -> FlowTranslator.toOnosFlow(policy(NetworkPolicy.Action.DROP, "ICMP", 80, null), mapper))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Nested
    class YangCodecAndAgentContract {
        private Vnf vnf() {
            Vnf v = new Vnf();
            v.setDefaultAction(VnfEnums.DefaultAction.DROP);
            v.setRulesRevision(7);
            return v;
        }

        private FirewallRule rule(long id, int prio, FirewallRule.Action a, boolean enabled) {
            FirewallRule r = new FirewallRule();
            r.setId(id);
            r.setName("r<" + id + ">&");
            r.setPriority(prio);
            r.setAction(a);
            r.setProtocol(FirewallRule.Protocol.TCP);
            r.setSourceCidr("10.10.0.11/32");
            r.setDestinationPort("80");
            r.setEnabled(enabled);
            r.setLogEnabled(true);
            return r;
        }

        @Test
        void encodesEscapedYangAndCanonicalFormIsOrderIndependent() {
            String a = FirewallYangCodec.encode(vnf(), List.of(rule(1, 10, FirewallRule.Action.ALLOW, true), rule(2, 20, FirewallRule.Action.DENY, true)));
            String b = FirewallYangCodec.encode(vnf(), List.of(rule(2, 20, FirewallRule.Action.DENY, true), rule(1, 10, FirewallRule.Action.ALLOW, true)));
            assertThat(a).contains("urn:nexus:nfv:firewall").contains("nc:operation=\"replace\"").contains("r&lt;1&gt;&amp;");
            assertThat(FirewallYangCodec.checksum(a)).isEqualTo(FirewallYangCodec.checksum(b));
            assertThat(FirewallYangCodec.ruleCount(a)).isEqualTo(2);
        }

        @Test
        void agentRulesetContainsOnlyEnabledRulesInPriorityOrder() {
            AgentModels.Ruleset rs = FirewallSyncService.toRuleset(vnf(), List.of(
                    rule(3, 30, FirewallRule.Action.DENY, true), rule(1, 10, FirewallRule.Action.ALLOW, true),
                    rule(2, 20, FirewallRule.Action.ALLOW, false)));
            assertThat(rs.revision()).isEqualTo(7);
            assertThat(rs.defaultAction()).isEqualTo("drop");
            assertThat(rs.rules()).extracting(AgentModels.Rule::id).containsExactly(1L, 3L);
            assertThat(rs.rules().get(0).action()).isEqualTo("accept");
            assertThat(rs.rules().get(1).action()).isEqualTo("drop");
        }
    }

    @Nested
    class CsvExport {
        @Test
        void escapesAndNeutralisesFormulas() {
            assertThat(Csv.row("a,b", "x\"y", "=cmd|' /C calc'!A0", null, 5))
                    .isEqualTo("\"a,b\",\"x\"\"y\",'=cmd|' /C calc'!A0,,5\r\n");
            assertThat(Csv.row("-1+1")).startsWith("'-1+1");
        }
    }

    @Nested
    class TelemetryMath {
        @Test
        void dockerStatsParsedLikeDockerCli() throws Exception {
            String json = """
                    {"cpu_stats":{"cpu_usage":{"total_usage":2000000000},"system_cpu_usage":20000000000,"online_cpus":2},
                     "precpu_stats":{"cpu_usage":{"total_usage":1000000000},"system_cpu_usage":10000000000},
                     "memory_stats":{"usage":104857600,"limit":268435456,"stats":{"inactive_file":52428800}},
                     "networks":{"eth0":{"rx_bytes":100,"tx_bytes":200},"eth1":{"rx_bytes":1000,"tx_bytes":2000}}}""";
            Class<?> c = Class.forName("com.nexus.nfv.vnf.orchestrator.DockerLabOrchestrator");
            Method m = c.getDeclaredMethod("parseStats", JsonNode.class);
            m.setAccessible(true);
            var usage = (com.nexus.nfv.vnf.orchestrator.VnfOrchestrator.ResourceUsage) m.invoke(null, new ObjectMapper().readTree(json));
            assertThat(usage.cpuPercent()).isEqualByComparingTo("20.00");   // (1e9/1e10)*2*100
            assertThat(usage.memoryUsageMb()).isEqualByComparingTo("50.00"); // 100MiB - 50MiB cache
            assertThat(usage.memoryPercent()).isEqualByComparingTo("19.53");
            assertThat(usage.rxBytes()).isEqualTo(1100);
            assertThat(usage.txBytes()).isEqualTo(2200);
        }

        @Test
        void counterDeltaHandlesTableRecreation() throws Exception {
            Class<?> c = Class.forName("com.nexus.nfv.traffic.TrafficCollector");
            Method diff = c.getDeclaredMethod("diff", AgentModels.Counter.class, AgentModels.Counter.class);
            diff.setAccessible(true);
            assertThat(diff.invoke(null, new AgentModels.Counter(150, 9000), new AgentModels.Counter(100, 6000)))
                    .isEqualTo(new AgentModels.Counter(50, 3000));
            // counters reset after a policy apply -> current value is the delta, never negative
            assertThat(diff.invoke(null, new AgentModels.Counter(5, 300), new AgentModels.Counter(100, 6000)))
                    .isEqualTo(new AgentModels.Counter(5, 300));
        }
    }
}
