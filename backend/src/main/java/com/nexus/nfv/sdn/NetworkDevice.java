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

@Entity
@Table(name = "network_devices")
public class NetworkDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, length = 128)
    private String deviceId;

    @Column(name = "device_type", length = 32)
    private String deviceType;

    @Column(length = 128)
    private String manufacturer;

    @Column(name = "hw_version", length = 128)
    private String hwVersion;

    @Column(name = "sw_version", length = 128)
    private String swVersion;

    @Column(length = 128)
    private String serial;

    @Column(length = 32)
    private String protocol;

    @Column(name = "management_address", length = 64)
    private String managementAddress;

    @Column(nullable = false)
    private boolean available;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 16)
    private DataSource dataSource;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getHwVersion() {
        return hwVersion;
    }

    public String getSwVersion() {
        return swVersion;
    }

    public String getSerial() {
        return serial;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getManagementAddress() {
        return managementAddress;
    }

    public boolean isAvailable() {
        return available;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public void setHwVersion(String hwVersion) {
        this.hwVersion = hwVersion;
    }

    public void setSwVersion(String swVersion) {
        this.swVersion = swVersion;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setManagementAddress(String managementAddress) {
        this.managementAddress = managementAddress;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
