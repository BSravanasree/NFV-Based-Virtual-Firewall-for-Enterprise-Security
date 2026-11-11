package com.nexus.nfv.firewall;

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

@Entity
@Table(name = "firewall_rules")
public class FirewallRule {

    public enum Action { ALLOW, DENY }

    public enum Protocol { ANY, TCP, UDP, ICMP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vnf_id", nullable = false)
    private Long vnfId;

    @Column(nullable = false, length = 64)
    private String name;

    /** Lower value = evaluated first. */
    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Protocol protocol;

    @Column(name = "source_cidr", length = 43)
    private String sourceCidr;

    @Column(name = "destination_cidr", length = 43)
    private String destinationCidr;

    @Column(name = "source_port", length = 11)
    private String sourcePort;

    @Column(name = "destination_port", length = 11)
    private String destinationPort;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "log_enabled", nullable = false)
    private boolean logEnabled = true;

    @Column(length = 255)
    private String description;

    @Column(name = "hit_packets", nullable = false)
    private long hitPackets;

    @Column(name = "hit_bytes", nullable = false)
    private long hitBytes;

    @Column(name = "counters_updated_at")
    private Instant countersUpdatedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String summary() {
        return action + " " + protocol + " " + (sourceCidr == null ? "any" : sourceCidr)
                + (sourcePort == null ? "" : ":" + sourcePort) + " -> "
                + (destinationCidr == null ? "any" : destinationCidr)
                + (destinationPort == null ? "" : ":" + destinationPort)
                + " prio=" + priority + (enabled ? "" : " (disabled)");
    }

    // ---- accessors

    public Long getId() {
        return id;
    }

    public Long getVnfId() {
        return vnfId;
    }

    public String getName() {
        return name;
    }

    public int getPriority() {
        return priority;
    }

    public Action getAction() {
        return action;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getSourceCidr() {
        return sourceCidr;
    }

    public String getDestinationCidr() {
        return destinationCidr;
    }

    public String getSourcePort() {
        return sourcePort;
    }

    public String getDestinationPort() {
        return destinationPort;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public String getDescription() {
        return description;
    }

    public long getHitPackets() {
        return hitPackets;
    }

    public long getHitBytes() {
        return hitBytes;
    }

    public Instant getCountersUpdatedAt() {
        return countersUpdatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setVnfId(Long vnfId) {
        this.vnfId = vnfId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public void setSourceCidr(String sourceCidr) {
        this.sourceCidr = sourceCidr;
    }

    public void setDestinationCidr(String destinationCidr) {
        this.destinationCidr = destinationCidr;
    }

    public void setSourcePort(String sourcePort) {
        this.sourcePort = sourcePort;
    }

    public void setDestinationPort(String destinationPort) {
        this.destinationPort = destinationPort;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setHitPackets(long hitPackets) {
        this.hitPackets = hitPackets;
    }

    public void setHitBytes(long hitBytes) {
        this.hitBytes = hitBytes;
    }

    public void setCountersUpdatedAt(Instant countersUpdatedAt) {
        this.countersUpdatedAt = countersUpdatedAt;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
