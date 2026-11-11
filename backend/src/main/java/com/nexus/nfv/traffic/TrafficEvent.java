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

/**
 * One logged packet header (NFLOG) from a firewall VNF: connection attempt metadata, not payload.
 * ALLOWED events are logged for the first packet of new connections, BLOCKED for every dropped packet
 * (rate-limited inside the VNF).
 */
@Entity
@Table(name = "traffic_events")
public class TrafficEvent {

    public enum Action { ALLOWED, BLOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vnf_id", nullable = false)
    private Long vnfId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Action action;

    @Column(nullable = false, length = 8)
    private String protocol;

    @Column(name = "src_ip", nullable = false, length = 45)
    private String srcIp;

    @Column(name = "dst_ip", nullable = false, length = 45)
    private String dstIp;

    @Column(name = "src_port")
    private Integer srcPort;

    @Column(name = "dst_port")
    private Integer dstPort;

    @Column(name = "rule_id")
    private Long ruleId;

    /** INBOUND (WAN -> LAN) or OUTBOUND (LAN -> WAN) */
    @Column(length = 8)
    private String direction;

    @Column(name = "packet_length")
    private Integer packetLength;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 16)
    private DataSource dataSource;

    @Column(name = "agent_seq")
    private Long agentSeq;

    // ---- accessors

    public Long getId() {
        return id;
    }

    public Long getVnfId() {
        return vnfId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Action getAction() {
        return action;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getSrcIp() {
        return srcIp;
    }

    public String getDstIp() {
        return dstIp;
    }

    public Integer getSrcPort() {
        return srcPort;
    }

    public Integer getDstPort() {
        return dstPort;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public String getDirection() {
        return direction;
    }

    public Integer getPacketLength() {
        return packetLength;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public Long getAgentSeq() {
        return agentSeq;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setVnfId(Long vnfId) {
        this.vnfId = vnfId;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }

    public void setDstIp(String dstIp) {
        this.dstIp = dstIp;
    }

    public void setSrcPort(Integer srcPort) {
        this.srcPort = srcPort;
    }

    public void setDstPort(Integer dstPort) {
        this.dstPort = dstPort;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setPacketLength(Integer packetLength) {
        this.packetLength = packetLength;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setAgentSeq(Long agentSeq) {
        this.agentSeq = agentSeq;
    }
}
