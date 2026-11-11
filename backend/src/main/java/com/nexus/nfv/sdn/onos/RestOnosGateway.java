package com.nexus.nfv.sdn.onos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.nfv.common.DataSource;
import com.nexus.nfv.common.exception.InfrastructureException;
import com.nexus.nfv.config.NexusProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.function.Function;

/** ONOS northbound REST API client (http://&lt;onos&gt;:8181/onos/v1). Credentials never leave the backend. */
public class RestOnosGateway implements OnosGateway {

    static final String SYSTEM = "ONOS";

    private final RestClient client;
    private final String url;

    public RestOnosGateway(NexusProperties.Onos cfg, RestClient.Builder builder) {
        this.url = cfg.url();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(10));
        String basic = Base64.getEncoder().encodeToString(
                (cfg.username() + ":" + (cfg.password() == null ? "" : cfg.password())).getBytes(StandardCharsets.UTF_8));
        this.client = builder.clone().requestFactory(f).baseUrl(url)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public DataSource dataSource() {
        return DataSource.LIVE;
    }

    @Override
    public String endpoint() {
        return url;
    }

    @Override
    public JsonNode controllerInfo() {
        return get("/cluster/local");
    }

    @Override
    public JsonNode devices() {
        return get("/devices").path("devices");
    }

    @Override
    public JsonNode ports(String deviceId) {
        return call(c -> c.get().uri("/devices/{id}/ports", deviceId).retrieve().body(JsonNode.class)).path("ports");
    }

    @Override
    public JsonNode portStatistics(String deviceId) {
        return call(c -> c.get().uri("/statistics/ports/{id}", deviceId).retrieve().body(JsonNode.class));
    }

    @Override
    public JsonNode hosts() {
        return get("/hosts").path("hosts");
    }

    @Override
    public JsonNode links() {
        return get("/links").path("links");
    }

    @Override
    public JsonNode flows(String deviceIdOrNull) {
        if (deviceIdOrNull == null) return get("/flows").path("flows");
        return call(c -> c.get().uri("/flows/{id}", deviceIdOrNull).retrieve().body(JsonNode.class)).path("flows");
    }

    @Override
    public JsonNode applications() {
        return get("/applications").path("applications");
    }

    @Override
    public String installFlow(String deviceId, ObjectNode flow, String appId) {
        ResponseEntity<Void> res = call(c -> c.post()
                .uri(u -> u.path("/flows/{id}").queryParam("appId", appId).build(deviceId))
                .contentType(MediaType.APPLICATION_JSON).body(flow).retrieve().toBodilessEntity());
        URI location = res.getHeaders().getLocation();
        if (location == null) {
            throw new InfrastructureException(SYSTEM, "flow accepted but no Location header returned");
        }
        String path = location.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    @Override
    public void removeFlow(String deviceId, String flowId) {
        call(c -> c.delete().uri("/flows/{d}/{f}", deviceId, flowId).retrieve().toBodilessEntity());
    }

    private JsonNode get(String path) {
        return call(c -> c.get().uri(path).retrieve().body(JsonNode.class));
    }

    private <T> T call(Function<RestClient, T> fn) {
        try {
            T r = fn.apply(client);
            if (r == null) throw new InfrastructureException(SYSTEM, "empty response");
            return r;
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            String msg = ex.getStatusCode().value() == 401 ? "authentication failed (check ONOS_USERNAME/ONOS_PASSWORD)"
                    : "HTTP " + ex.getStatusCode().value() + ": " + (body.length() > 300 ? body.substring(0, 300) : body);
            throw new InfrastructureException(SYSTEM, msg);
        } catch (RestClientException ex) {
            throw new InfrastructureException(SYSTEM, "unreachable at " + url + " ("
                    + ex.getMostSpecificCause().getMessage() + ")", ex);
        }
    }
}
