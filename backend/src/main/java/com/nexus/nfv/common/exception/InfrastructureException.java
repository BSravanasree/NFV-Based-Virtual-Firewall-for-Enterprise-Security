package com.nexus.nfv.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an external system (OSM, ONOS, Docker Engine, NETCONF server, firewall agent)
 * is unreachable or rejects an operation. Surfaces as HTTP 502 so the UI never mistakes an
 * infrastructure failure for success.
 */
public class InfrastructureException extends ApiException {

    private final String system;

    public InfrastructureException(String system, String message) {
        super(HttpStatus.BAD_GATEWAY, "INFRASTRUCTURE_ERROR", system + ": " + message);
        this.system = system;
    }

    public InfrastructureException(String system, String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "INFRASTRUCTURE_ERROR", system + ": " + message, cause);
        this.system = system;
    }

    public String getSystem() {
        return system;
    }

    public static InfrastructureException notConfigured(String system, String hint) {
        return new InfrastructureException(system, "integration is not configured (" + hint + ")");
    }
}
