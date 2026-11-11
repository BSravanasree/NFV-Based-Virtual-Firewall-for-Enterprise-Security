package com.nexus.nfv.vnf.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.nfv.common.exception.InfrastructureException;
import com.nexus.nfv.config.NexusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

/**
 * Client for the Open Source MANO Northbound Interface (NBI, ETSI SOL005 flavoured REST API).
 * Implements token authentication with caching and the NS/VNF lifecycle calls used by the platform.
 * Target: OSM Release 14+ (default NBI endpoint https://&lt;osm-host&gt;:9999/osm).
 */
@Component
public class OsmNbiClient {

    private static final Logger log = LoggerFactory.getLogger(OsmNbiClient.class);
    static final String SYSTEM = "OSM NBI";

    private final NexusProperties.Osm cfg;
    private final RestClient client;
    private volatile String token;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public OsmNbiClient(NexusProperties props, RestClient.Builder builder) {
        this.cfg = props.orchestrator().osm();
        HttpClient.Builder hb = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5));
        if (!cfg.verifyTls()) {
            log.warn("OSM_VERIFY_TLS=false: NBI TLS certificate validation is DISABLED (lab use only)");
            hb.sslContext(trustAll());
        }
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(hb.build());
        f.setReadTimeout(Duration.ofSeconds(30));
        String base = cfg.nbiUrl() == null ? "" : cfg.nbiUrl().replaceAll("/+$", "");
        this.client = builder.clone().requestFactory(f).baseUrl(base).build();
    }

    public boolean configured() {
        return cfg.nbiUrl() != null && !cfg.nbiUrl().isBlank() && cfg.password() != null && !cfg.password().isBlank();
    }

    public String endpoint() {
        return cfg.nbiUrl();
    }

    public NexusProperties.Osm config() {
        return cfg;
    }

    public JsonNode version() {
        return get("/version");
    }

    public JsonNode get(String path) {
        return call(c -> c.get().uri(path).header(HttpHeaders.AUTHORIZATION, bearer())
                .accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class));
    }

    public JsonNode post(String path, Map<String, Object> body) {
        return call(c -> c.post().uri(path).header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class));
    }

    public void delete(String path) {
        call(c -> c.delete().uri(path).header(HttpHeaders.AUTHORIZATION, bearer()).retrieve().toBodilessEntity());
    }

    /** Polls an NS LCM operation occurrence until it completes, fails or the timeout elapses. */
    public JsonNode awaitOperation(String opId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            JsonNode op = get("/nslcm/v1/ns_lcm_op_occs/" + opId);
            String state = op.path("operationState").asText();
            if ("COMPLETED".equals(state)) return op;
            if (state.startsWith("FAILED") || "ROLLED_BACK".equals(state)) {
                throw new InfrastructureException(SYSTEM, "operation " + op.path("lcmOperationType").asText()
                        + " " + state + ": " + op.path("detailed-status").asText(op.path("errorMessage").asText()));
            }
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfrastructureException(SYSTEM, "interrupted while waiting for operation " + opId);
            }
        }
        throw new InfrastructureException(SYSTEM, "operation " + opId + " did not complete within " + timeout);
    }

    private synchronized String bearer() {
        if (!configured()) {
            throw InfrastructureException.notConfigured(SYSTEM, "set OSM_NBI_URL and OSM_PASSWORD");
        }
        if (token == null || Instant.now().isAfter(tokenExpiry.minusSeconds(60))) {
            JsonNode t = call(c -> c.post().uri("/admin/v1/tokens").contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", cfg.username(), "password", cfg.password(), "project_id", cfg.project()))
                    .retrieve().body(JsonNode.class));
            token = t.path("id").asText(t.path("_id").asText());
            double exp = t.path("expires").asDouble(0);
            tokenExpiry = exp > 0 ? Instant.ofEpochMilli((long) (exp * 1000)) : Instant.now().plusSeconds(3000);
        }
        return "Bearer " + token;
    }

    private <T> T call(Function<RestClient, T> fn) {
        try {
            return fn.apply(client);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401) {
                token = null;
            }
            String body = ex.getResponseBodyAsString();
            throw new InfrastructureException(SYSTEM, "HTTP " + ex.getStatusCode().value() + ": "
                    + (body.length() > 400 ? body.substring(0, 400) : body).trim());
        } catch (RestClientException ex) {
            throw new InfrastructureException(SYSTEM, "unreachable at " + cfg.nbiUrl() + " ("
                    + ex.getMostSpecificCause().getMessage() + ")", ex);
        }
    }

    private static SSLContext trustAll() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {
                }

                public void checkServerTrusted(X509Certificate[] c, String a) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
