package com.nexus.nfv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Strongly-typed platform configuration. Every secret is sourced from the environment
 * (see application.yml); nothing sensitive is committed to the repository.
 */
@ConfigurationProperties(prefix = "nexus")
public record NexusProperties(
        Security security,
        Orchestrator orchestrator,
        Agent agent,
        Onos onos,
        Monitoring monitoring) {

    public record Security(
            String jwtSecret,
            int jwtTtlMinutes,
            int maxFailedLogins,
            int lockoutMinutes,
            List<String> corsAllowedOrigins,
            String credentialKey,
            Bootstrap bootstrap) {
    }

    public record Bootstrap(
            String adminUsername,
            String adminPassword,
            String adminEmail,
            String demoUsersPassword) {
    }

    public record Orchestrator(String mode, Docker docker, Osm osm) {
    }

    public record Docker(
            String apiUrl,
            String vnfImage,
            String mgmtNetwork,
            String lanNetwork,
            String wanNetwork,
            String lanCidr,
            String wanCidr,
            int agentPort,
            boolean netconfEnabled) {
    }

    public record Osm(
            String nbiUrl,
            String username,
            String password,
            String project,
            String vimAccount,
            String nsdId,
            boolean verifyTls) {
    }

    public record Agent(String token, int connectTimeoutMs, int readTimeoutMs) {
    }

    public record Onos(String mode, String url, String username, String password, String appId) {
        public boolean demo() {
            return "demo".equalsIgnoreCase(mode);
        }
    }

    public record Monitoring(long pollIntervalMs, long integrationCheckIntervalMs, long detectionIntervalMs) {
    }
}
