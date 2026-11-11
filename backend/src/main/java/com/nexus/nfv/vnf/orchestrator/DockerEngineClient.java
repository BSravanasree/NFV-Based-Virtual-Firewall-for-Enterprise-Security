package com.nexus.nfv.vnf.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.nfv.common.exception.InfrastructureException;
import com.nexus.nfv.config.NexusProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Minimal Docker Engine REST API client. In the reference deployment the backend talks to a
 * docker-socket-proxy that only exposes the container/network/image endpoints (never the raw socket).
 */
@Component
public class DockerEngineClient {

    static final String SYSTEM = "Docker Engine";

    private final RestClient client;
    private final String endpoint;

    public DockerEngineClient(NexusProperties props, RestClient.Builder builder) {
        this.endpoint = props.orchestrator().docker().apiUrl();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(http);
        f.setReadTimeout(Duration.ofSeconds(60));
        this.client = builder.clone().requestFactory(f).baseUrl(endpoint).build();
    }

    public String endpoint() {
        return endpoint;
    }

    public JsonNode version() {
        return call(c -> c.get().uri("/version").retrieve().body(JsonNode.class));
    }

    public boolean imageExists(String image) {
        try {
            client.get().uri("/images/{name}/json", image).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return false;
            throw wrap(ex);
        } catch (RestClientException ex) {
            throw wrap(ex);
        }
    }

    public String createContainer(String name, Map<String, Object> spec) {
        JsonNode res = call(c -> c.post().uri(u -> u.path("/containers/create").queryParam("name", name).build())
                .contentType(MediaType.APPLICATION_JSON).body(spec).retrieve().body(JsonNode.class));
        return res.path("Id").asText();
    }

    public void connectNetwork(String network, String containerId, String ipv4) {
        Map<String, Object> body = ipv4 == null || ipv4.isBlank()
                ? Map.of("Container", containerId)
                : Map.of("Container", containerId,
                "EndpointConfig", Map.of("IPAMConfig", Map.of("IPv4Address", ipv4)));
        call(c -> c.post().uri("/networks/{n}/connect", network).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity());
    }

    public void start(String id) {
        // 304 = already started, treated as success
        exchangeIgnoring(c -> c.post().uri("/containers/{id}/start", id).retrieve()
                .onStatus(s -> s.value() == 304, (req, res) -> {
                }).toBodilessEntity());
    }

    public void stop(String id, int timeoutSeconds) {
        exchangeIgnoring(c -> c.post().uri(u -> u.path("/containers/{id}/stop").queryParam("t", timeoutSeconds).build(id))
                .retrieve().onStatus(s -> s.value() == 304, (req, res) -> {
                }).toBodilessEntity());
    }

    public void restart(String id, int timeoutSeconds) {
        call(c -> c.post().uri(u -> u.path("/containers/{id}/restart").queryParam("t", timeoutSeconds).build(id))
                .retrieve().toBodilessEntity());
    }

    public void update(String id, long nanoCpus, long memoryBytes) {
        call(c -> c.post().uri("/containers/{id}/update", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("NanoCpus", nanoCpus, "Memory", memoryBytes, "MemorySwap", memoryBytes))
                .retrieve().toBodilessEntity());
    }

    public void remove(String id) {
        try {
            client.delete().uri(u -> u.path("/containers/{id}").queryParam("force", true).queryParam("v", true).build(id))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) throw wrap(ex);
        } catch (RestClientException ex) {
            throw wrap(ex);
        }
    }

    public Optional<JsonNode> inspect(String idOrName) {
        try {
            return Optional.ofNullable(client.get().uri("/containers/{id}/json", idOrName).retrieve().body(JsonNode.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return Optional.empty();
            throw wrap(ex);
        } catch (RestClientException ex) {
            throw wrap(ex);
        }
    }

    /** One stats sample (stream=false returns current + previous CPU readings, needed for CPU %). */
    public JsonNode stats(String id) {
        return call(c -> c.get().uri(u -> u.path("/containers/{id}/stats").queryParam("stream", false).build(id))
                .retrieve().body(JsonNode.class));
    }

    public Optional<JsonNode> network(String name) {
        try {
            return Optional.ofNullable(client.get().uri("/networks/{n}", name).retrieve().body(JsonNode.class));
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) return Optional.empty();
            throw wrap(ex);
        } catch (RestClientException ex) {
            throw wrap(ex);
        }
    }

    private <T> T call(Function<RestClient, T> fn) {
        try {
            return fn.apply(client);
        } catch (RestClientException ex) {
            throw wrap(ex);
        }
    }

    private void exchangeIgnoring(Function<RestClient, ?> fn) {
        call(fn);
    }

    private InfrastructureException wrap(RestClientException ex) {
        if (ex instanceof RestClientResponseException r) {
            HttpStatusCode code = r.getStatusCode();
            String body = r.getResponseBodyAsString();
            return new InfrastructureException(SYSTEM, "HTTP " + code.value() + ": "
                    + (body.length() > 400 ? body.substring(0, 400) : body).trim());
        }
        return new InfrastructureException(SYSTEM, "unreachable at " + endpoint + " (" + ex.getMostSpecificCause().getMessage() + ")", ex);
    }
}
