package com.nexus.nfv.vnf.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.nfv.common.exception.InfrastructureException;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEnums;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Production orchestrator backed by OSM. A VNF maps to one NS instance of the onboarded
 * {@code nexus_fw-ns} descriptor (see osm/ packages). Start/stop/restart use the NS "operate" LCM
 * operation (VDU changeStateTo), scaling uses the VNFD scaling-group (horizontal) or "verticalscale".
 *
 * <p>Verification status: implemented against the OSM NBI API reference; it has not been exercised against
 * a live OSM installation in this repository's CI (see docs/OSM_INTEGRATION.md).</p>
 */
@Component
public class OsmOrchestrator implements VnfOrchestrator {

    private static final Duration INSTANTIATE_TIMEOUT = Duration.ofMinutes(20);
    private static final Duration OPERATION_TIMEOUT = Duration.ofMinutes(10);
    static final String VDU_ID = "fw-vdu";
    static final String SCALING_GROUP = "fw-scale-out";
    static final int AGENT_PORT = 9443;

    private final OsmNbiClient osm;
    private final NexusProperties.Osm cfg;

    public OsmOrchestrator(OsmNbiClient osm, NexusProperties props) {
        this.osm = osm;
        this.cfg = props.orchestrator().osm();
    }

    @Override
    public VnfEnums.OrchestratorType type() {
        return VnfEnums.OrchestratorType.OSM;
    }

    @Override
    public Deployment instantiate(Vnf vnf, DeployOptions options) {
        if (cfg.vimAccount() == null || cfg.vimAccount().isBlank()) {
            throw InfrastructureException.notConfigured(OsmNbiClient.SYSTEM, "OSM_VIM_ACCOUNT is not set");
        }
        String nsdId = resolveNsdId(cfg.nsdId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nsName", vnf.getName());
        body.put("nsdId", nsdId);
        body.put("vimAccountId", cfg.vimAccount());
        body.put("nsDescription", "NEXUS firewall VNF " + vnf.getName());
        JsonNode res = osm.post("/nslcm/v1/ns_instances_content", body);
        String nsId = res.path("id").asText();
        String opId = res.path("nslcmop_id").asText();
        osm.awaitOperation(opId, INSTANTIATE_TIMEOUT);

        JsonNode vnfr = vnfRecord(nsId);
        String mgmtIp = vnfr.path("ip-address").asText(null);
        String lanIp = null;
        String wanIp = null;
        for (JsonNode vdur : vnfr.path("vdur")) {
            for (JsonNode itf : vdur.path("interfaces")) {
                String n = itf.path("name").asText();
                if (n.contains("lan")) lanIp = itf.path("ip-address").asText(null);
                if (n.contains("wan") || n.contains("dmz")) wanIp = itf.path("ip-address").asText(null);
            }
        }
        if (mgmtIp == null) {
            throw new InfrastructureException(OsmNbiClient.SYSTEM, "NS " + nsId + " instantiated but VNF has no management IP");
        }
        return new Deployment(nsId, mgmtIp, lanIp, wanIp, "http://" + mgmtIp + ":" + AGENT_PORT, mgmtIp, 830,
                "OSM NS instance " + nsId + " COMPLETED");
    }

    @Override
    public void start(Vnf vnf) {
        operate(vnf, "start");
    }

    @Override
    public void stop(Vnf vnf) {
        operate(vnf, "stop");
    }

    @Override
    public void restart(Vnf vnf) {
        operate(vnf, "stop");
        operate(vnf, "start");
    }

    @Override
    public void scale(Vnf vnf, BigDecimal cpus, int memoryMb, ScaleDirection direction) {
        String nsId = vnf.getExternalId();
        JsonNode res;
        if (direction == ScaleDirection.VERTICAL) {
            String vnfInstanceId = vnfRecord(nsId).path("_id").asText();
            res = osm.post("/nslcm/v1/ns_instances/" + nsId + "/verticalscale", Map.of(
                    "lcmOperationType", "verticalscale",
                    "verticalScale", "CHANGE_VNFFLAVOR",
                    "nsInstanceId", nsId,
                    "changeVnfFlavorData", Map.of("vnfInstanceId", vnfInstanceId, "additionalParams", Map.of(
                            "vduid", VDU_ID,
                            "virtualMemory", memoryMb,
                            "numVirtualCpu", cpus.setScale(0, java.math.RoundingMode.CEILING).intValue()))));
        } else {
            res = osm.post("/nslcm/v1/ns_instances/" + nsId + "/scale", Map.of(
                    "scaleType", "SCALE_VNF",
                    "scaleVnfData", Map.of(
                            "scaleVnfType", direction.name(),
                            "scaleByStepData", Map.of("scaling-group-descriptor", SCALING_GROUP,
                                    "member-vnf-index", "1"))));
        }
        osm.awaitOperation(res.path("id").asText(), OPERATION_TIMEOUT);
    }

    @Override
    public void terminate(Vnf vnf) {
        String nsId = vnf.getExternalId();
        if (nsId == null) return;
        JsonNode res = osm.post("/nslcm/v1/ns_instances/" + nsId + "/terminate", Map.of("autoremove", true));
        osm.awaitOperation(res.path("id").asText(), OPERATION_TIMEOUT);
    }

    @Override
    public Optional<RuntimeStatus> inspect(Vnf vnf) {
        if (vnf.getExternalId() == null) return Optional.empty();
        JsonNode ns = osm.get("/nslcm/v1/ns_instances/" + vnf.getExternalId());
        String opState = ns.path("operational-status").asText(ns.path("nsState").asText());
        return Optional.of(new RuntimeStatus("running".equalsIgnoreCase(opState) || "READY".equalsIgnoreCase(opState),
                opState, ns.path("detailed-status").asText("")));
    }

    /** OSM exposes VIM metrics via its MON/Prometheus stack, not the NBI; usage is collected by the agent. */
    @Override
    public Optional<ResourceUsage> usage(Vnf vnf) {
        return Optional.empty();
    }

    @Override
    public ConnectionCheck check() {
        if (!osm.configured()) {
            return new ConnectionCheck(false, osm.endpoint(), -1, "Not configured (OSM_NBI_URL / OSM_PASSWORD)");
        }
        long t0 = System.nanoTime();
        try {
            JsonNode v = osm.version();
            return new ConnectionCheck(true, osm.endpoint(), (System.nanoTime() - t0) / 1_000_000,
                    "OSM NBI " + v.path("version").asText("?"));
        } catch (RuntimeException ex) {
            return new ConnectionCheck(false, osm.endpoint(), -1, ex.getMessage());
        }
    }

    private void operate(Vnf vnf, String changeStateTo) {
        String nsId = vnf.getExternalId();
        String vnfInstanceId = vnfRecord(nsId).path("_id").asText();
        JsonNode res = osm.post("/nslcm/v1/ns_instances/" + nsId + "/operate", Map.of(
                "lcmOperationType", "operate",
                "nsInstanceId", nsId,
                "operateVnfData", Map.of(
                        "vnfInstanceId", vnfInstanceId,
                        "changeStateTo", changeStateTo,
                        "additionalParam", Map.of("run-day1", false, "vdu_id", VDU_ID, "count-index", 0))));
        osm.awaitOperation(res.path("id").asText(), OPERATION_TIMEOUT);
    }

    private JsonNode vnfRecord(String nsId) {
        JsonNode list = osm.get("/nslcm/v1/vnf_instances?nsr-id-ref=" + nsId);
        if (!list.isArray() || list.isEmpty()) {
            throw new InfrastructureException(OsmNbiClient.SYSTEM, "no VNF record for NS " + nsId);
        }
        return list.get(0);
    }

    private String resolveNsdId(String idOrName) {
        JsonNode nsds = osm.get("/nsd/v1/ns_descriptors");
        for (JsonNode n : nsds) {
            if (idOrName.equals(n.path("_id").asText()) || idOrName.equals(n.path("id").asText())
                    || idOrName.equals(n.path("name").asText())) {
                return n.path("_id").asText();
            }
        }
        throw new InfrastructureException(OsmNbiClient.SYSTEM, "NS descriptor '" + idOrName
                + "' is not onboarded. Upload osm/nexus_fw_ns package first.");
    }
}
