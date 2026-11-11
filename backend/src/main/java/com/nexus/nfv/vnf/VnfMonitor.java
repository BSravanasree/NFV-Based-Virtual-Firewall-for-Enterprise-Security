package com.nexus.nfv.vnf;

import com.nexus.nfv.firewall.agent.AgentModels;
import com.nexus.nfv.firewall.agent.FirewallAgentClient;
import com.nexus.nfv.vnf.orchestrator.OrchestratorRegistry;
import com.nexus.nfv.vnf.orchestrator.VnfOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodic VNF observation: runtime status (detects unexpected exits), agent health and resource usage.
 * Metrics are stored only when actually measured – no interpolation or synthetic values.
 */
@Component
public class VnfMonitor {

    private static final Logger log = LoggerFactory.getLogger(VnfMonitor.class);

    private final VnfRepository vnfs;
    private final VnfMetricSampleRepository samples;
    private final VnfLifecycleEventRepository events;
    private final OrchestratorRegistry orchestrators;
    private final FirewallAgentClient agent;
    private final TransactionTemplate tx;
    private final Map<Long, long[]> lastNet = new ConcurrentHashMap<>(); // vnfId -> [rx, tx, epochMs]

    public VnfMonitor(VnfRepository vnfs, VnfMetricSampleRepository samples, VnfLifecycleEventRepository events,
                      OrchestratorRegistry orchestrators, FirewallAgentClient agent, PlatformTransactionManager txm) {
        this.vnfs = vnfs;
        this.samples = samples;
        this.events = events;
        this.orchestrators = orchestrators;
        this.agent = agent;
        this.tx = new TransactionTemplate(txm);
    }

    @Scheduled(fixedDelayString = "${nexus.monitoring.poll-interval-ms:10000}", initialDelay = 15000)
    public void poll() {
        List<Vnf> active = vnfs.findByStateIn(EnumSet.of(VnfState.ACTIVE));
        for (Vnf v : active) {
            try {
                observe(v);
            } catch (RuntimeException ex) {
                log.debug("Monitoring of VNF {} failed: {}", v.getName(), ex.getMessage());
            }
        }
    }

    void observe(Vnf snapshot) {
        VnfOrchestrator orch = orchestrators.forType(snapshot.getOrchestrator());
        Optional<VnfOrchestrator.RuntimeStatus> status;
        try {
            status = orch.inspect(snapshot);
        } catch (RuntimeException ex) {
            // orchestrator unreachable: we cannot tell; mark health unknown but don't change lifecycle state
            update(snapshot.getId(), v -> v.setHealth(VnfEnums.Health.UNKNOWN));
            return;
        }
        if (status.isEmpty() || !status.get().running()) {
            String detail = status.map(s -> "runtime state '" + s.rawState() + "' " + s.detail()).orElse("resource no longer exists");
            update(snapshot.getId(), v -> {
                if (v.getState() != VnfState.ACTIVE) return;
                v.setState(VnfState.FAILED);
                v.setHealth(VnfEnums.Health.UNREACHABLE);
                v.setLastErrorTruncated("Monitor detected unexpected exit: " + detail);
                VnfLifecycleEvent e = new VnfLifecycleEvent();
                e.setVnfId(v.getId());
                e.setOperation(VnfEnums.Operation.HEAL);
                e.setFromState(VnfState.ACTIVE);
                e.setToState(VnfState.FAILED);
                e.setOutcome("FAILED");
                e.setMessage("Unexpected exit detected by monitor: " + detail);
                e.setActor("monitor");
                e.setOccurredAt(Instant.now());
                events.save(e);
            });
            log.warn("VNF {} is no longer running ({})", snapshot.getName(), detail);
            return;
        }

        VnfEnums.Health health;
        try {
            AgentModels.Health h = agent.health(snapshot.getAgentUrl());
            boolean revisionOk = h.appliedRevision() == snapshot.getAppliedRevision();
            health = ("ok".equalsIgnoreCase(h.status()) && h.nftables() && revisionOk)
                    ? VnfEnums.Health.HEALTHY : VnfEnums.Health.DEGRADED;
        } catch (RuntimeException ex) {
            health = VnfEnums.Health.UNREACHABLE;
        }

        Optional<VnfOrchestrator.ResourceUsage> usage = Optional.empty();
        try {
            usage = orch.usage(snapshot);
        } catch (RuntimeException ex) {
            log.debug("Usage sample failed for {}: {}", snapshot.getName(), ex.getMessage());
        }

        final VnfEnums.Health fh = health;
        final Optional<VnfOrchestrator.ResourceUsage> fu = usage;
        Instant now = Instant.now();
        update(snapshot.getId(), v -> {
            v.setHealth(fh);
            fu.ifPresent(u -> {
                Long bps = null;
                long[] prev = lastNet.get(v.getId());
                long total = u.rxBytes() + u.txBytes();
                if (prev != null && now.toEpochMilli() > prev[2] && total >= prev[0] + prev[1]) {
                    double secs = (now.toEpochMilli() - prev[2]) / 1000.0;
                    bps = (long) ((total - prev[0] - prev[1]) * 8 / secs);
                }
                lastNet.put(v.getId(), new long[]{u.rxBytes(), u.txBytes(), now.toEpochMilli()});
                v.setCpuPercent(u.cpuPercent());
                v.setMemoryPercent(u.memoryPercent());
                v.setMemoryUsageMb(u.memoryUsageMb());
                v.setRxBytes(u.rxBytes());
                v.setTxBytes(u.txBytes());
                v.setThroughputBps(bps);
                v.setMetricsSource(v.dataSource());
                v.setMetricsUpdatedAt(now);

                VnfMetricSample s = new VnfMetricSample();
                s.setVnfId(v.getId());
                s.setSampledAt(now);
                s.setCpuPercent(u.cpuPercent());
                s.setMemoryPercent(u.memoryPercent());
                s.setMemoryUsageMb(u.memoryUsageMb());
                s.setRxBytes(u.rxBytes());
                s.setTxBytes(u.txBytes());
                s.setThroughputBps(bps);
                s.setDataSource(v.dataSource());
                samples.save(s);
            });
        });
    }

    private void update(Long id, java.util.function.Consumer<Vnf> fn) {
        try {
            tx.executeWithoutResult(s -> vnfs.findById(id).ifPresent(v -> {
                if (v.getState() != VnfState.ACTIVE) return; // a lifecycle operation owns the VNF now
                fn.accept(v);
                vnfs.save(v);
            }));
        } catch (org.springframework.dao.OptimisticLockingFailureException ex) {
            log.debug("Skipped monitor update of VNF {} due to concurrent lifecycle change", id);
        }
    }
}
