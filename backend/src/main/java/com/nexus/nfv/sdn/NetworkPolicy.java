package com.nexus.nfv.sdn;

import com.nexus.nfv.common.DataSource;
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

/** Security policy expressed at the SDN layer and compiled into an OpenFlow flow rule on one switch. */
@Entity
@Table(name = "network_policies")
public class NetworkPolicy {

    public enum Action { DROP, ALLOW, FORWARD, STEER_TO_FIREWALL }

    public enum Status { PENDING, INSTALLED, FAILED, REMOVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(nullable = false)
    private int priority;

    /** ANY, TCP, UDP, ICMP */
    @Column(name = "ip_protocol", nullable = false, length = 8)
    private String ipProtocol;

    @Column(name = "source_cidr", length = 43)
    private String sourceCidr;

    @Column(name = "destination_cidr", length = 43)
    private String destinationCidr;

    @Column(name = "destination_port")
    private Integer destinationPort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Action action;

    @Column(name = "output_port", length = 32)
    private String outputPort;

    @Column(name = "onos_flow_id", length = 64)
    private String onosFlowId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", length = 16)
    private DataSource dataSource;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    // ---- accessors

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public int getPriority() {
        return priority;
    }

    public String getIpProtocol() {
        return ipProtocol;
    }

    public String getSourceCidr() {
        return sourceCidr;
    }

    public String getDestinationCidr() {
        return destinationCidr;
    }

    public Integer getDestinationPort() {
        return destinationPort;
    }

    public Action getAction() {
        return action;
    }

    public String getOutputPort() {
        return outputPort;
    }

    public String getOnosFlowId() {
        return onosFlowId;
    }

    public Status getStatus() {
        return status;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public String getLastError() {
        return lastError;
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

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setIpProtocol(String ipProtocol) {
        this.ipProtocol = ipProtocol;
    }

    public void setSourceCidr(String sourceCidr) {
        this.sourceCidr = sourceCidr;
    }

    public void setDestinationCidr(String destinationCidr) {
        this.destinationCidr = destinationCidr;
    }

    public void setDestinationPort(Integer destinationPort) {
        this.destinationPort = destinationPort;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public void setOutputPort(String outputPort) {
        this.outputPort = outputPort;
    }

    public void setOnosFlowId(String onosFlowId) {
        this.onosFlowId = onosFlowId;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
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
}
