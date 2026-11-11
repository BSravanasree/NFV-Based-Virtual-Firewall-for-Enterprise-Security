package com.nexus.nfv.traffic;

import com.nexus.nfv.common.DataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Per-interval deltas of nftables counters of one VNF (exact packet/byte counts, all traffic). */
@Entity
@Table(name = "traffic_samples")
public class TrafficSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vnf_id", nullable = false)
    private Long vnfId;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "interval_ms", nullable = false)
    private long intervalMs;

    @Column(name = "allowed_packets", nullable = false)
    private long allowedPackets;

    @Column(name = "allowed_bytes", nullable = false)
    private long allowedBytes;

    @Column(name = "blocked_packets", nullable = false)
    private long blockedPackets;

    @Column(name = "blocked_bytes", nullable = false)
    private long blockedBytes;

    @Column(name = "tcp_packets", nullable = false)
    private long tcpPackets;

    @Column(name = "udp_packets", nullable = false)
    private long udpPackets;

    @Column(name = "icmp_packets", nullable = false)
    private long icmpPackets;

    @Column(name = "inbound_bytes", nullable = false)
    private long inboundBytes;

    @Column(name = "outbound_bytes", nullable = false)
    private long outboundBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 16)
    private DataSource dataSource;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public Long getVnfId() {
        return vnfId;
    }

    public Instant getSampledAt() {
        return sampledAt;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public long getAllowedPackets() {
        return allowedPackets;
    }

    public long getAllowedBytes() {
        return allowedBytes;
    }

    public long getBlockedPackets() {
        return blockedPackets;
    }

    public long getBlockedBytes() {
        return blockedBytes;
    }

    public long getTcpPackets() {
        return tcpPackets;
    }

    public long getUdpPackets() {
        return udpPackets;
    }

    public long getIcmpPackets() {
        return icmpPackets;
    }

    public long getInboundBytes() {
        return inboundBytes;
    }

    public long getOutboundBytes() {
        return outboundBytes;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setVnfId(Long vnfId) {
        this.vnfId = vnfId;
    }

    public void setSampledAt(Instant sampledAt) {
        this.sampledAt = sampledAt;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public void setAllowedPackets(long allowedPackets) {
        this.allowedPackets = allowedPackets;
    }

    public void setAllowedBytes(long allowedBytes) {
        this.allowedBytes = allowedBytes;
    }

    public void setBlockedPackets(long blockedPackets) {
        this.blockedPackets = blockedPackets;
    }

    public void setBlockedBytes(long blockedBytes) {
        this.blockedBytes = blockedBytes;
    }

    public void setTcpPackets(long tcpPackets) {
        this.tcpPackets = tcpPackets;
    }

    public void setUdpPackets(long udpPackets) {
        this.udpPackets = udpPackets;
    }

    public void setIcmpPackets(long icmpPackets) {
        this.icmpPackets = icmpPackets;
    }

    public void setInboundBytes(long inboundBytes) {
        this.inboundBytes = inboundBytes;
    }

    public void setOutboundBytes(long outboundBytes) {
        this.outboundBytes = outboundBytes;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
