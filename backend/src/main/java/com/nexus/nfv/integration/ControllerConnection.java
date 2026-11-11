package com.nexus.nfv.integration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Last known health of an external control-plane system (ONOS, OSM, Docker, NETCONF device). */
@Entity
@Table(name = "controller_connections")
public class ControllerConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ONOS, OSM, DOCKER, NETCONF */
    @Column(name = "controller_type", nullable = false, length = 16)
    private String controllerType;

    @Column(nullable = false, length = 64)
    private String name;

    @Column
    private String endpoint;

    /** REAL, LAB, DEMO, DISABLED */
    @Column(nullable = false, length = 16)
    private String mode;

    /** UP, DOWN, UNKNOWN, DISABLED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(length = 2000)
    private String details;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public String getControllerType() {
        return controllerType;
    }

    public String getName() {
        return name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getMode() {
        return mode;
    }

    public String getStatus() {
        return status;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public String getDetails() {
        return details;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setControllerType(String controllerType) {
        this.controllerType = controllerType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public void setLastSuccessAt(Instant lastSuccessAt) {
        this.lastSuccessAt = lastSuccessAt;
    }
}
