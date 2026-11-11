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
@Table(name = "topology_links")
public class TopologyLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "src_device", nullable = false, length = 128)
    private String srcDevice;

    @Column(name = "src_port", nullable = false, length = 32)
    private String srcPort;

    @Column(name = "dst_device", nullable = false, length = 128)
    private String dstDevice;

    @Column(name = "dst_port", nullable = false, length = 32)
    private String dstPort;

    @Column(name = "link_type", length = 32)
    private String linkType;

    @Column(length = 16)
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 16)
    private DataSource dataSource;

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public String getSrcDevice() {
        return srcDevice;
    }

    public String getSrcPort() {
        return srcPort;
    }

    public String getDstDevice() {
        return dstDevice;
    }

    public String getDstPort() {
        return dstPort;
    }

    public String getLinkType() {
        return linkType;
    }

    public String getState() {
        return state;
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

    public void setSrcDevice(String srcDevice) {
        this.srcDevice = srcDevice;
    }

    public void setSrcPort(String srcPort) {
        this.srcPort = srcPort;
    }

    public void setDstDevice(String dstDevice) {
        this.dstDevice = dstDevice;
    }

    public void setDstPort(String dstPort) {
        this.dstPort = dstPort;
    }

    public void setLinkType(String linkType) {
        this.linkType = linkType;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
