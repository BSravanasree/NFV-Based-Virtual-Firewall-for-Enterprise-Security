package com.nexus.nfv.vnf;

import com.nexus.nfv.common.DataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "vnf_metric_samples")
public class VnfMetricSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vnf_id", nullable = false)
    private Long vnfId;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "cpu_percent", precision = 6, scale = 2)
    private BigDecimal cpuPercent;

    @Column(name = "memory_percent", precision = 6, scale = 2)
    private BigDecimal memoryPercent;

    @Column(name = "memory_usage_mb", precision = 10, scale = 2)
    private BigDecimal memoryUsageMb;

    @Column(name = "rx_bytes")
    private Long rxBytes;

    @Column(name = "tx_bytes")
    private Long txBytes;

    @Column(name = "throughput_bps")
    private Long throughputBps;

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

    public BigDecimal getCpuPercent() {
        return cpuPercent;
    }

    public BigDecimal getMemoryPercent() {
        return memoryPercent;
    }

    public BigDecimal getMemoryUsageMb() {
        return memoryUsageMb;
    }

    public Long getRxBytes() {
        return rxBytes;
    }

    public Long getTxBytes() {
        return txBytes;
    }

    public Long getThroughputBps() {
        return throughputBps;
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

    public void setCpuPercent(BigDecimal cpuPercent) {
        this.cpuPercent = cpuPercent;
    }

    public void setMemoryPercent(BigDecimal memoryPercent) {
        this.memoryPercent = memoryPercent;
    }

    public void setMemoryUsageMb(BigDecimal memoryUsageMb) {
        this.memoryUsageMb = memoryUsageMb;
    }

    public void setRxBytes(Long rxBytes) {
        this.rxBytes = rxBytes;
    }

    public void setTxBytes(Long txBytes) {
        this.txBytes = txBytes;
    }

    public void setThroughputBps(Long throughputBps) {
        this.throughputBps = throughputBps;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }
}
