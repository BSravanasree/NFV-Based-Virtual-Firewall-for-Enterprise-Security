package com.nexus.nfv.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.nfv.alert.SecurityAlertRepository;
import com.nexus.nfv.alert.ThreatDetectionService;
import com.nexus.nfv.audit.AuditLogRepository;
import com.nexus.nfv.common.DataSource;
import com.nexus.nfv.traffic.TrafficEvent;
import com.nexus.nfv.traffic.TrafficEventRepository;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import com.nexus.nfv.vnf.VnfRepository;
import com.nexus.nfv.vnf.VnfState;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests of the control plane against H2 (MySQL mode, same Flyway migrations as production).
 * A stub firewall agent (real HTTP server) verifies the policy push contract and token authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlatformIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired VnfRepository vnfs;
    @Autowired TrafficEventRepository events;
    @Autowired SecurityAlertRepository alerts;
    @Autowired AuditLogRepository audit;
    @Autowired ThreatDetectionService detection;

    private HttpServer agent;
    private final List<String> agentRequests = new CopyOnWriteArrayList<>();
    private volatile String lastRulesetBody;

    @BeforeAll
    void startStubAgent() throws IOException {
        agent = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        agent.createContext("/", exchange -> {
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            agentRequests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] body;
            int code = 200;
            String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
            if (!"Bearer test-agent-token-0123456789".equals(auth)) {
                code = 401;
                body = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            } else if ("PUT".equals(exchange.getRequestMethod()) && contentLength == null) {
                // mirror the real agent: chunked bodies (no Content-Length) are rejected
                code = 411;
                body = "{\"error\":\"Content-Length required\"}".getBytes(StandardCharsets.UTF_8);
            } else if ("PUT".equals(exchange.getRequestMethod())) {
                lastRulesetBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                long rev = mapper.readTree(lastRulesetBody).path("revision").asLong();
                int count = mapper.readTree(lastRulesetBody).path("rules").size();
                body = ("{\"appliedRevision\":" + rev + ",\"ruleCount\":" + count + ",\"appliedAt\":\"" + Instant.now() + "\"}")
                        .getBytes(StandardCharsets.UTF_8);
            } else {
                body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        agent.start();
    }

    @AfterAll
    void stopStubAgent() {
        agent.stop(0);
    }

    private String login(String user, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + mapper.readTree(r.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private String admin() throws Exception {
        return login("admin", "Adm1n!TestPass");
    }

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ authentication & RBAC

    @Test
    @Order(1)
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/vnfs")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/vnfs").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void badCredentialsAreGenericAndAudited() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid username or password"));
        assertThat(audit.findAll()).anyMatch(a -> a.getAction().equals("USER_LOGIN_FAILED") && a.getUsername().equals("nobody"));
    }

    @Test
    @Order(3)
    void accountLocksAfterRepeatedFailures() throws Exception {
        String admin = admin();
        mvc.perform(post("/api/v1/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"locktest\",\"fullName\":\"Lock Test\",\"email\":\"lock@test.local\",\"role\":\"SECURITY_ANALYST\",\"password\":\"L0ck!TestPass\"}"))
                .andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"locktest\",\"password\":\"bad-" + i + "\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"locktest\",\"password\":\"L0ck!TestPass\"}"))
                .andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @Order(4)
    void logoutRevokesTheToken() throws Exception {
        String token = admin();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void roleBasedAccessIsEnforced() throws Exception {
        String analyst = login("analyst", "Dem0!UserPass");
        String operator = login("operator", "Dem0!UserPass");
        String vnfBody = "{\"name\":\"fw-rbac\",\"vnfType\":\"FIREWALL\",\"cpuLimit\":0.5,\"memoryLimitMb\":256}";
        // analysts can read but not change infrastructure
        mvc.perform(get("/api/v1/vnfs").header("Authorization", analyst)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/vnfs").header("Authorization", analyst).contentType(MediaType.APPLICATION_JSON).content(vnfBody))
                .andExpect(status().isForbidden());
        // operators cannot administer users, read the audit trail or change settings
        mvc.perform(get("/api/v1/users").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", operator)).andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/settings/detection.enabled")
                        .header("Authorization", operator).contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"false\"}"))
                .andExpect(status().isForbidden());
        // analysts can read the audit trail; operators can create VNFs
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", analyst)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/vnfs").header("Authorization", operator).contentType(MediaType.APPLICATION_JSON).content(vnfBody))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.state").value("CREATED"));
        // dashboard activity hides audit records from operators
        mvc.perform(get("/api/v1/dashboard/activity").header("Authorization", operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.audit").isEmpty());
    }

    @Test
    @Order(6)
    void weakPasswordsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/users").header("Authorization", admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weak\",\"fullName\":\"Weak\",\"email\":\"weak@test.local\",\"role\":\"SECURITY_ANALYST\",\"password\":\"password123\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ VNF lifecycle

    @Test
    @Order(10)
    void vnfValidationAndIllegalTransitions() throws Exception {
        String admin = admin();
        mvc.perform(post("/api/v1/vnfs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Name!\",\"vnfType\":\"FIREWALL\",\"cpuLimit\":9,\"memoryLimitMb\":10}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations").isArray());
        mvc.perform(post("/api/v1/vnfs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"fw-cidr\",\"vnfType\":\"FIREWALL\",\"cpuLimit\":1,\"memoryLimitMb\":256,\"lanIp\":\"192.168.1.1\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("10.10.0.0/24")));

        MvcResult created = mvc.perform(post("/api/v1/vnfs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"fw-lifecycle\",\"vnfType\":\"FIREWALL\",\"cpuLimit\":0.5,\"memoryLimitMb\":256}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lanIp").value("10.10.0.254"))
                .andExpect(jsonPath("$.defaultAction").value("DROP")).andReturn();
        long id = json(created).path("id").asLong();
        // cannot stop/start a VNF that was never deployed
        mvc.perform(post("/api/v1/vnfs/" + id + "/stop").header("Authorization", admin)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
        mvc.perform(post("/api/v1/vnfs/" + id + "/start").header("Authorization", admin)).andExpect(status().isConflict());
    }

    @Test
    @Order(11)
    void deployFailsHonestlyWhenInfrastructureIsUnavailable() throws Exception {
        String admin = admin();
        long id = json(mvc.perform(post("/api/v1/vnfs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"fw-nodocker\",\"vnfType\":\"FIREWALL\",\"cpuLimit\":0.5,\"memoryLimitMb\":256}")).andReturn()).path("id").asLong();
        mvc.perform(post("/api/v1/vnfs/" + id + "/deploy").header("Authorization", admin))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.state").value("INSTANTIATING"));
        // Docker API points to 127.0.0.1:1 in tests -> must end in FAILED, never ACTIVE
        Vnf v = null;
        for (int i = 0; i < 50; i++) {
            v = vnfs.findById(id).orElseThrow();
            if (v.getState() != VnfState.INSTANTIATING) break;
            Thread.sleep(100);
        }
        assertThat(v.getState()).isEqualTo(VnfState.FAILED);
        assertThat(v.getLastError()).contains("Docker Engine");
        MvcResult ev = mvc.perform(get("/api/v1/vnfs/" + id + "/events").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        assertThat(json(ev).toString()).contains("\"outcome\":\"FAILED\"").contains("\"outcome\":\"STARTED\"");
        // FAILED without resources can be deleted
        mvc.perform(delete("/api/v1/vnfs/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------ firewall policy + agent sync

    @Test
    @Order(20)
    void firewallRulesArePushedToTheAgentAndConfirmed() throws Exception {
        String admin = admin();
        long id = json(mvc.perform(post("/api/v1/vnfs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"fw-sync\",\"vnfType\":\"FIREWALL\",\"cpuLimit\":0.5,\"memoryLimitMb\":256}")).andReturn()).path("id").asLong();

        // rules on a non-active VNF are stored but not enforced
        MvcResult pending = mvc.perform(post("/api/v1/firewall/vnfs/" + id + "/rules").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"allow-web\",\"priority\":10,\"action\":\"ALLOW\",\"protocol\":\"TCP\",\"sourceCidr\":\"10.10.0.11\",\"destinationCidr\":\"10.20.0.10\",\"destinationPort\":\"80\"}"))
                .andExpect(status().isCreated()).andReturn();
        assertThat(json(pending).path("sync").path("applied").asBoolean()).isFalse();
        assertThat(json(pending).path("sync").path("attempted").asBoolean()).isFalse();
        long ruleId = json(pending).path("rule").path("id").asLong();

        // simulate an ACTIVE VNF whose agent is the stub server
        Vnf v = vnfs.findById(id).orElseThrow();
        v.setState(VnfState.ACTIVE);
        v.setAgentUrl("http://127.0.0.1:" + agent.getAddress().getPort());
        vnfs.save(v);

        MvcResult applied = mvc.perform(post("/api/v1/firewall/vnfs/" + id + "/rules").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"deny-ssh\",\"priority\":20,\"action\":\"DENY\",\"protocol\":\"TCP\",\"destinationPort\":\"22\"}"))
                .andExpect(status().isCreated()).andReturn();
        JsonNode sync = json(applied).path("sync");
        assertThat(sync.path("applied").asBoolean()).isTrue();
        assertThat(sync.path("status").asText()).isEqualTo("IN_SYNC");

        JsonNode pushed = mapper.readTree(lastRulesetBody);
        assertThat(pushed.path("defaultAction").asText()).isEqualTo("drop");
        assertThat(pushed.path("rules")).hasSize(2);
        assertThat(pushed.path("rules").get(0).path("srcCidr").asText()).isEqualTo("10.10.0.11/32");
        assertThat(pushed.path("rules").get(1).path("action").asText()).isEqualTo("drop");

        // disabling a rule removes it from the enforced ruleset
        mvc.perform(patch("/api/v1/firewall/rules/" + ruleId + "/enabled").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sync.applied").value(true));
        assertThat(mapper.readTree(lastRulesetBody).path("rules")).hasSize(1);

        // reorder renumbers priorities and re-applies
        long denyId = json(applied).path("rule").path("id").asLong();
        mvc.perform(post("/api/v1/firewall/vnfs/" + id + "/rules/reorder").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ruleIds\":[" + denyId + "," + ruleId + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rules[0].id").value(denyId))
                .andExpect(jsonPath("$.rules[0].priority").value(10)).andExpect(jsonPath("$.syncStatus").value("IN_SYNC"));

        assertThat(agentRequests).contains("PUT /v1/ruleset");
        assertThat(audit.findAll()).anyMatch(a -> a.getAction().equals("FIREWALL_RULE_CREATED") && a.getDetails().contains("deny-ssh"));
    }

    @Test
    @Order(21)
    void unreachableAgentIsReportedAsNotEnforced() throws Exception {
        String admin = admin();
        long id = json(mvc.perform(post("/api/v1/vnfs").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"fw-down\",\"vnfType\":\"FIREWALL\",\"cpuLimit\":0.5,\"memoryLimitMb\":256}")).andReturn()).path("id").asLong();
        Vnf v = vnfs.findById(id).orElseThrow();
        v.setState(VnfState.ACTIVE);
        v.setAgentUrl("http://127.0.0.1:1");
        vnfs.save(v);
        mvc.perform(post("/api/v1/firewall/vnfs/" + id + "/rules").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"r1\",\"priority\":10,\"action\":\"ALLOW\",\"protocol\":\"ICMP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sync.attempted").value(true))
                .andExpect(jsonPath("$.sync.applied").value(false))
                .andExpect(jsonPath("$.sync.status").value("FAILED"));
        assertThat(vnfs.findById(id).orElseThrow().getSyncStatus()).isEqualTo(VnfEnums.SyncStatus.FAILED);
    }

    // ------------------------------------------------------------------ detection, SDN demo, reports

    @Test
    @Order(30)
    void portScanAndBruteForceAreDetectedAndDeduplicated() throws Exception {
        Vnf v = vnfs.findAll().get(0);
        Instant now = Instant.now();
        for (int port = 1; port <= 15; port++) events.save(event(v.getId(), "10.10.0.66", port, now.minusSeconds(port)));
        for (int i = 0; i < 6; i++) events.save(event(v.getId(), "10.10.0.77", 22, now.minusSeconds(i)));

        int first = detection.runDetection(now);
        assertThat(first).isGreaterThan(0);
        assertThat(alerts.findAll()).anyMatch(a -> a.getDetector().equals("PORT_SCAN") && a.getSourceIp().equals("10.10.0.66"));
        assertThat(alerts.findAll()).anyMatch(a -> a.getDetector().equals("UNAUTHORIZED_ACCESS") && a.getSourceIp().equals("10.10.0.77"));
        long count = alerts.count();
        detection.runDetection(now); // same evidence -> no duplicates
        assertThat(alerts.count()).isEqualTo(count);

        String analyst = login("analyst", "Dem0!UserPass");
        long alertId = alerts.findAll().get(0).getId();
        mvc.perform(patch("/api/v1/alerts/" + alertId).header("Authorization", analyst).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/alerts/" + alertId).header("Authorization", analyst).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESOLVED\",\"note\":\"Blocked lab scanner\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(get("/api/v1/traffic/top").header("Authorization", analyst)).andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedSources[0].key").value("10.10.0.66"));
    }

    private static TrafficEvent event(Long vnfId, String src, int port, Instant at) {
        TrafficEvent e = new TrafficEvent();
        e.setVnfId(vnfId);
        e.setOccurredAt(at);
        e.setAction(TrafficEvent.Action.BLOCKED);
        e.setProtocol("TCP");
        e.setSrcIp(src);
        e.setDstIp("10.20.0.10");
        e.setSrcPort(40000);
        e.setDstPort(port);
        e.setDirection("OUTBOUND");
        e.setDataSource(DataSource.LAB);
        return e;
    }

    @Test
    @Order(40)
    void sdnDemoModeIsLabelledAndPoliciesCompileToFlows() throws Exception {
        String admin = admin();
        mvc.perform(get("/api/v1/sdn/status").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DEMO")).andExpect(jsonPath("$.dataSource").value("DEMO"));
        mvc.perform(get("/api/v1/sdn/devices").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataSource").value("DEMO"));
        MvcResult p = mvc.perform(post("/api/v1/sdn/policies").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"block-client2-ssh\",\"deviceId\":\"of:0000000000000001\",\"priority\":40001,\"ipProtocol\":\"TCP\",\"sourceCidr\":\"10.0.1.11\",\"destinationPort\":22,\"action\":\"DROP\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INSTALLED"))
                .andExpect(jsonPath("$.dataSource").value("DEMO")).andReturn();
        String flowId = json(p).path("onosFlowId").asText();
        MvcResult flows = mvc.perform(get("/api/v1/sdn/flows").param("deviceId", "of:0000000000000001").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(flows).toString()).contains(flowId).contains("ipv4_src=10.0.1.11/32").contains("tcp_dst=22");
        mvc.perform(delete("/api/v1/sdn/policies/" + json(p).path("id").asLong()).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/sdn/topology").param("view", "sdn").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true)).andExpect(jsonPath("$.nodes[0].type").value("CONTROLLER"));
    }

    @Test
    @Order(50)
    void reportsAndExportsAreGenerated() throws Exception {
        String admin = admin();
        MvcResult pdf = mvc.perform(get("/api/v1/reports/security.pdf").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        MvcResult csv = mvc.perform(get("/api/v1/audit-logs/export").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        assertThat(csv.getResponse().getContentAsString()).startsWith("id,timestamp,username,action");
        mvc.perform(get("/api/v1/dashboard/summary").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.posture.level").exists()).andExpect(jsonPath("$.vnfs.total").isNumber());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.info.title").exists());
    }

    @Test
    @Order(51)
    void secretsAreNeverExposed() throws Exception {
        String admin = admin();
        MvcResult cfg = mvc.perform(get("/api/v1/integrations/config").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        String body = cfg.getResponse().getContentAsString();
        assertThat(body).doesNotContain("test-agent-token").doesNotContain("test-only-jwt-secret").contains("\"agentTokenConfigured\":true");
        mvc.perform(post("/api/v1/netconf/devices").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ext-nc\",\"host\":\"192.0.2.10\",\"port\":830,\"username\":\"netconf\",\"password\":\"S3cret!Value\"}"))
                .andExpect(status().isCreated());
        MvcResult list = mvc.perform(get("/api/v1/netconf/devices").header("Authorization", admin)).andExpect(status().isOk()).andReturn();
        assertThat(list.getResponse().getContentAsString()).doesNotContain("S3cret!Value").doesNotContain("passwordEnc");
    }
}
