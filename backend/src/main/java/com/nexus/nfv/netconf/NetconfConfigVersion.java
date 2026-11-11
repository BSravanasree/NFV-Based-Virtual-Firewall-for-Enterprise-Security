package com.nexus.nfv.netconf;

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
@Table(name = "netconf_config_versions")
public class NetconfConfigVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** PUSH, ROLLBACK, RETRIEVE */
    @Column(nullable = false, length = 16)
    private String operation;

    @Column(nullable = false, length = 16)
    private String datastore;

    @Column(name = "config_xml", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String configXml;

    @Column(nullable = false, length = 64)
    private String checksum;

    /** APPLIED, FAILED, RETRIEVED */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 1000)
    private String message;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getOperation() {
        return operation;
    }

    public String getDatastore() {
        return datastore;
    }

    public String getConfigXml() {
        return configXml;
    }

    public String getChecksum() {
        return checksum;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public void setVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setDatastore(String datastore) {
        this.datastore = datastore;
    }

    public void setConfigXml(String configXml) {
        this.configXml = configXml;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
