package com.nexus.nfv.sdn.onos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nfv.common.DataSource;
import com.nexus.nfv.common.exception.ApiException;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * DEMO MODE – no SDN controller involved. Serves a fixture topology (classpath:demo/onos-demo-topology.json,
 * shaped exactly like ONOS REST responses) that mirrors the reference Mininet lab, and keeps installed flows
 * in memory. Every response is tagged {@link DataSource#DEMO} and the UI renders a persistent banner.
 * Nothing in the network is changed by this implementation.
 */
public class DemoOnosGateway implements OnosGateway {

    private final ObjectNode fixture;
    private final ArrayNode flows;
    private final AtomicLong flowSeq = new AtomicLong(0x00D3_0000_0000_0000L);

    public DemoOnosGateway(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("demo/onos-demo-topology.json").getInputStream()) {
            this.fixture = (ObjectNode) mapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("Demo topology fixture missing", e);
        }
        this.flows = (ArrayNode) fixture.path("flows");
    }

    @Override
    public DataSource dataSource() {
        return DataSource.DEMO;
    }

    @Override
    public String endpoint() {
        return "demo://fixture";
    }

    @Override
    public JsonNode controllerInfo() {
        return fixture.path("cluster");
    }

    @Override
    public JsonNode devices() {
        return fixture.path("devices");
    }

    @Override
    public JsonNode ports(String deviceId) {
        JsonNode p = fixture.path("ports").path(deviceId);
        if (p.isMissingNode()) throw ApiException.notFound("Device", deviceId);
        return p;
    }

    @Override
    public JsonNode portStatistics(String deviceId) {
        return fixture.path("portStatistics").path(deviceId);
    }

    @Override
    public JsonNode hosts() {
        return fixture.path("hosts");
    }

    @Override
    public JsonNode links() {
        return fixture.path("links");
    }

    @Override
    public synchronized JsonNode flows(String deviceIdOrNull) {
        if (deviceIdOrNull == null) return flows.deepCopy();
        ArrayNode out = flows.arrayNode();
        flows.forEach(f -> {
            if (deviceIdOrNull.equals(f.path("deviceId").asText())) out.add(f);
        });
        return out;
    }

    @Override
    public JsonNode applications() {
        return fixture.path("applications");
    }

    @Override
    public synchronized String installFlow(String deviceId, ObjectNode flow, String appId) {
        boolean known = false;
        for (JsonNode d : devices()) known |= deviceId.equals(d.path("id").asText());
        if (!known) throw ApiException.notFound("Device", deviceId);
        String id = String.valueOf(flowSeq.incrementAndGet());
        ObjectNode stored = flow.deepCopy();
        stored.put("id", id);
        stored.put("deviceId", deviceId);
        stored.put("appId", appId);
        stored.put("state", "ADDED");
        stored.put("packets", 0);
        stored.put("bytes", 0);
        stored.put("life", 0);
        flows.add(stored);
        return id;
    }

    @Override
    public synchronized void removeFlow(String deviceId, String flowId) {
        Iterator<JsonNode> it = flows.iterator();
        while (it.hasNext()) {
            JsonNode f = it.next();
            if (flowId.equals(f.path("id").asText()) && deviceId.equals(f.path("deviceId").asText())) {
                it.remove();
                return;
            }
        }
        throw ApiException.notFound("Flow", flowId);
    }
}
