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
@Table(name = "network_hosts")
public class NetworkHost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "host_id", nullable = false, length = 128)
    private String hostId;

    @Column(nullable = false, length = 32)
    private String mac;

    @Column(length = 16)
    private String vlan;

    @Column(name = "ip_addresses")
    private String ipAddresses;

    @Column(name = "location_device", length = 128)
    private String locationDevice;

    @Column(name = "location_port", length = 32)
    private String locationPort;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 16)
    private DataSource dataSource;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public String getHostId() {
        return hostId;
    }

    public String getMac() {
        return mac;
    }

    public String getVlan() {
        return vlan;
    }

    public String getIpAddresses() {
        return ipAddresses;
    }

    public String getLocationDevice() {
        return locationDevice;
    }

    public String getLocationPort() {
        return locationPort;
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

    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public void setVlan(String vlan) {
        this.vlan = vlan;
    }

    public void setIpAddresses(String ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    public void setLocationDevice(String locationDevice) {
        this.locationDevice = locationDevice;
    }

    public void setLocationPort(String locationPort) {
        this.locationPort = locationPort;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
