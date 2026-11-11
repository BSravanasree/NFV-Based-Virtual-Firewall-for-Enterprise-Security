package com.nexus.nfv.vnf;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "vnf_lifecycle_events")
public class VnfLifecycleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vnf_id", nullable = false)
    private Long vnfId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VnfEnums.Operation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 24)
    private VnfState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 24)
    private VnfState toState;

    /** STARTED, SUCCEEDED, FAILED */
    @Column(nullable = false, length = 16)
    private String outcome;

    @Column(length = 1000)
    private String message;

    @Column(nullable = false, length = 64)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public Long getVnfId() {
        return vnfId;
    }

    public VnfEnums.Operation getOperation() {
        return operation;
    }

    public VnfState getFromState() {
        return fromState;
    }

    public VnfState getToState() {
        return toState;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setVnfId(Long vnfId) {
        this.vnfId = vnfId;
    }

    public void setOperation(VnfEnums.Operation operation) {
        this.operation = operation;
    }

    public void setFromState(VnfState fromState) {
        this.fromState = fromState;
    }

    public void setToState(VnfState toState) {
        this.toState = toState;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
