package com.nexus.nfv.firewall.agent;

import com.nexus.nfv.common.exception.InfrastructureException;
import com.nexus.nfv.config.NexusProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Function;

/**
 * REST client for the firewall VNF agent. The agent is reachable only on the management network and
 * authenticates the control plane with a shared bearer token (NEXUS_AGENT_TOKEN). The token is never
 * logged or returned to the frontend.
 */
@Component
public class FirewallAgentClient {

    private static final String SYSTEM = "Firewall agent";

    private final NexusProperties.Agent cfg;
    private final RestClient.Builder builder;

    public FirewallAgentClient(NexusProperties props, RestClient.Builder builder) {
        this.cfg = props.agent();
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.connectTimeoutMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(cfg.readTimeoutMs()));
        // Buffering guarantees a Content-Length header: the agent (Python http.server) does not accept
        // chunked transfer encoding, which the JDK client would otherwise use for streamed bodies.
        this.builder = builder.clone().requestFactory(new BufferingClientHttpRequestFactory(factory));
    }

    public AgentModels.Health health(String baseUrl) {
        return call(baseUrl, c -> c.get().uri("/health").retrieve().body(AgentModels.Health.class));
    }

    public AgentModels.ApplyResult applyRuleset(String baseUrl, AgentModels.Ruleset ruleset) {
        return call(baseUrl, c -> c.put().uri("/v1/ruleset")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ruleset)
                .retrieve()
                .body(AgentModels.ApplyResult.class));
    }

    public AgentModels.CurrentRuleset currentRuleset(String baseUrl) {
        return call(baseUrl, c -> c.get().uri("/v1/ruleset").retrieve().body(AgentModels.CurrentRuleset.class));
    }

    public AgentModels.Counters counters(String baseUrl) {
        return call(baseUrl, c -> c.get().uri("/v1/counters").retrieve().body(AgentModels.Counters.class));
    }

    public AgentModels.Events events(String baseUrl, long since, int limit) {
        return call(baseUrl, c -> c.get().uri(u -> u.path("/v1/events")
                        .queryParam("since", since).queryParam("limit", limit).build())
                .retrieve().body(AgentModels.Events.class));
    }

    private <T> T call(String baseUrl, Function<RestClient, T> fn) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new InfrastructureException(SYSTEM, "VNF has no agent endpoint (not deployed)");
        }
        if (cfg.token() == null || cfg.token().isBlank()) {
            throw InfrastructureException.notConfigured(SYSTEM, "NEXUS_AGENT_TOKEN is not set");
        }
        RestClient client = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + cfg.token())
                .build();
        try {
            T result = fn.apply(client);
            if (result == null) {
                throw new InfrastructureException(SYSTEM, "empty response from " + baseUrl);
            }
            return result;
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            throw new InfrastructureException(SYSTEM, "HTTP " + ex.getStatusCode().value() + " from agent: "
                    + (body.length() > 300 ? body.substring(0, 300) : body));
        } catch (RestClientException ex) {
            throw new InfrastructureException(SYSTEM, "unreachable at " + baseUrl + " (" + rootMessage(ex) + ")", ex);
        }
    }

    static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getMessage();
        return r.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }
}
