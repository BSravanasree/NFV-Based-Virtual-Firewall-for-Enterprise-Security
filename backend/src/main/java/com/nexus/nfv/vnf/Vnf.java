package com.nexus.nfv.vnf;

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

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "vnfs")
public class Vnf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 63)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "vnf_type", nullable = false, length = 32)
    private VnfEnums.VnfType vnfType;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VnfEnums.OrchestratorType orchestrator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private VnfState state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VnfEnums.Health health;

    /** Container id (Docker lab) or NS instance id (OSM). */
    @Column(name = "external_id", length = 128)
    private String externalId;

    @Column(name = "mgmt_ip", length = 45)
    private String mgmtIp;

    @Column(name = "lan_ip", length = 45)
    private String lanIp;

    @Column(name = "wan_ip", length = 45)
    private String wanIp;

    @Column(name = "agent_url")
    private String agentUrl;

    @Column(name = "cpu_limit", nullable = false, precision = 5, scale = 2)
    private BigDecimal cpuLimit;

    @Column(name = "memory_limit_mb", nullable = false)
    private int memoryLimitMb;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_action", nullable = false, length = 8)
    private VnfEnums.DefaultAction defaultAction = VnfEnums.DefaultAction.DROP;

    /** Incremented on every change to the desired firewall policy. */
    @Column(name = "rules_revision", nullable = false)
    private long rulesRevision;

    /** Revision confirmed as applied by the firewall agent. */
    @Column(name = "applied_revision", nullable = false)
    private long appliedRevision;

    @Column(name = "rules_applied_at")
    private Instant rulesAppliedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    private VnfEnums.SyncStatus syncStatus = VnfEnums.SyncStatus.PENDING;

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
    @Column(name = "metrics_source", length = 16)
    private DataSource metricsSource;

    @Column(name = "metrics_updated_at")
    private Instant metricsUpdatedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

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

    public DataSource dataSource() {
        return orchestrator == VnfEnums.OrchestratorType.OSM ? DataSource.LIVE : DataSource.LAB;
    }

    public void setLastErrorTruncated(String error) {
        this.lastError = error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error);
    }

    // ---- accessors

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public VnfEnums.VnfType getVnfType() {
        return vnfType;
    }

    public String getDescription() {
        return description;
    }

    public VnfEnums.OrchestratorType getOrchestrator() {
        return orchestrator;
    }

    public VnfState getState() {
        return state;
    }

    public VnfEnums.Health getHealth() {
        return health;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getMgmtIp() {
        return mgmtIp;
    }

    public String getLanIp() {
        return lanIp;
    }

    public String getWanIp() {
        return wanIp;
    }

    public String getAgentUrl() {
        return agentUrl;
    }

    public BigDecimal getCpuLimit() {
        return cpuLimit;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public VnfEnums.DefaultAction getDefaultAction() {
        return defaultAction;
    }

    public long getRulesRevision() {
        return rulesRevision;
    }

    public long getAppliedRevision() {
        return appliedRevision;
    }

    public Instant getRulesAppliedAt() {
        return rulesAppliedAt;
    }

    public VnfEnums.SyncStatus getSyncStatus() {
        return syncStatus;
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

    public DataSource getMetricsSource() {
        return metricsSource;
    }

    public Instant getMetricsUpdatedAt() {
        return metricsUpdatedAt;
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

    public long getVersion() {
        return version;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVnfType(VnfEnums.VnfType vnfType) {
        this.vnfType = vnfType;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setOrchestrator(VnfEnums.OrchestratorType orchestrator) {
        this.orchestrator = orchestrator;
    }

    public void setState(VnfState state) {
        this.state = state;
    }

    public void setHealth(VnfEnums.Health health) {
        this.health = health;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public void setMgmtIp(String mgmtIp) {
        this.mgmtIp = mgmtIp;
    }

    public void setLanIp(String lanIp) {
        this.lanIp = lanIp;
    }

    public void setWanIp(String wanIp) {
        this.wanIp = wanIp;
    }

    public void setAgentUrl(String agentUrl) {
        this.agentUrl = agentUrl;
    }

    public void setCpuLimit(BigDecimal cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    public void setMemoryLimitMb(int memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }

    public void setDefaultAction(VnfEnums.DefaultAction defaultAction) {
        this.defaultAction = defaultAction;
    }

    public void setRulesRevision(long rulesRevision) {
        this.rulesRevision = rulesRevision;
    }

    public void setAppliedRevision(long appliedRevision) {
        this.appliedRevision = appliedRevision;
    }

    public void setRulesAppliedAt(Instant rulesAppliedAt) {
        this.rulesAppliedAt = rulesAppliedAt;
    }

    public void setSyncStatus(VnfEnums.SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
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

    public void setMetricsSource(DataSource metricsSource) {
        this.metricsSource = metricsSource;
    }

    public void setMetricsUpdatedAt(Instant metricsUpdatedAt) {
        this.metricsUpdatedAt = metricsUpdatedAt;
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

    public void setVersion(long version) {
        this.version = version;
    }
}
