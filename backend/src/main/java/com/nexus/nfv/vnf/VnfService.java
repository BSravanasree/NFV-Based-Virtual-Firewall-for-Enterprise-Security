package com.nexus.nfv.vnf;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.security.CredentialCipher;
import com.nexus.nfv.security.CurrentUser;
import com.nexus.nfv.security.NexusPrincipal;
import com.nexus.nfv.vnf.orchestrator.OrchestratorRegistry;
import com.nexus.nfv.vnf.orchestrator.VnfOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * VNF lifecycle management. Each operation:
 * <ol>
 *   <li>validates the state-machine transition and moves the VNF into a transitional state (committed);</li>
 *   <li>executes the orchestrator call on the lifecycle executor;</li>
 *   <li>records the confirmed final state, or FAILED with the infrastructure error. Never a fake success.</li>
 * </ol>
 */
@Service
public class VnfService {

    private static final Logger log = LoggerFactory.getLogger(VnfService.class);

    private final VnfRepository vnfs;
    private final VnfLifecycleEventRepository events;
    private final VnfMetricSampleRepository samples;
    private final OrchestratorRegistry orchestrators;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;
    private final TaskExecutor executor;
    private final TransactionTemplate tx;
    private final CredentialCipher cipher;
    private final NexusProperties props;

    public VnfService(VnfRepository vnfs, VnfLifecycleEventRepository events, VnfMetricSampleRepository samples,
                      OrchestratorRegistry orchestrators, AuditService audit, ApplicationEventPublisher publisher,
                      @Qualifier("lifecycleExecutor") TaskExecutor executor, PlatformTransactionManager txm,
                      CredentialCipher cipher, NexusProperties props) {
        this.vnfs = vnfs;
        this.events = events;
        this.samples = samples;
        this.orchestrators = orchestrators;
        this.audit = audit;
        this.publisher = publisher;
        this.executor = executor;
        this.tx = new TransactionTemplate(txm);
        this.cipher = cipher;
        this.props = props;
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<VnfDtos.VnfDto> list() {
        return vnfs.findAllByOrderByCreatedAtDesc().stream().map(VnfDtos.VnfDto::from).toList();
    }

    @Transactional(readOnly = true)
    public VnfDtos.VnfDto get(Long id) {
        return VnfDtos.VnfDto.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<VnfDtos.LifecycleEventDto> events(Long id) {
        find(id);
        return events.findByVnfIdOrderByOccurredAtDesc(id).stream().map(VnfDtos.LifecycleEventDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<VnfDtos.MetricSampleDto> metrics(Long id, Duration window) {
        find(id);
        return samples.findByVnfIdAndSampledAtAfterOrderBySampledAtAsc(id, Instant.now().minus(window))
                .stream().map(VnfDtos.MetricSampleDto::from).toList();
    }

    // ------------------------------------------------------------------ create / configure / delete

    @Transactional
    public VnfDtos.VnfDto create(VnfDtos.CreateVnfRequest req) {
        if (vnfs.existsByName(req.name())) throw ApiException.conflict("A VNF named '" + req.name() + "' already exists");
        VnfOrchestrator orchestrator = orchestrators.defaultOrchestrator();

        Vnf v = new Vnf();
        v.setName(req.name());
        v.setVnfType(req.vnfType());
        v.setDescription(req.description());
        v.setOrchestrator(orchestrator.type());
        v.setState(VnfState.CREATED);
        v.setHealth(VnfEnums.Health.UNKNOWN);
        v.setCpuLimit(req.cpuLimit());
        v.setMemoryLimitMb(req.memoryLimitMb());
        v.setDefaultAction(req.defaultAction() == null ? VnfEnums.DefaultAction.DROP : req.defaultAction());
        v.setSyncStatus(VnfEnums.SyncStatus.PENDING);
        v.setRulesRevision(1);
        v.setCreatedBy(CurrentUser.username());

        if (orchestrator.type() == VnfEnums.OrchestratorType.DOCKER) {
            var d = props.orchestrator().docker();
            String lan = req.lanIp() == null ? defaultGatewayIp(d.lanCidr()) : req.lanIp();
            String wan = req.wanIp() == null ? defaultGatewayIp(d.wanCidr()) : req.wanIp();
            requireInCidr(lan, d.lanCidr(), "lanIp");
            requireInCidr(wan, d.wanCidr(), "wanIp");
            v.setLanIp(lan);
            v.setWanIp(wan);
        }
        vnfs.save(v);
        recordEvent(v.getId(), VnfEnums.Operation.CREATE, null, VnfState.CREATED, "SUCCEEDED",
                "VNF record created (orchestrator " + orchestrator.type() + ")");
        audit.success(AuditAction.VNF_CREATED, "VNF", "VNF", v.getId(),
                "Created VNF " + v.getName() + " [" + v.getVnfType() + ", " + v.getCpuLimit() + " vCPU, "
                        + v.getMemoryLimitMb() + " MiB]");
        return VnfDtos.VnfDto.from(v);
    }

    @Transactional
    public VnfDtos.VnfDto configure(Long id, VnfDtos.ConfigureVnfRequest req) {
        Vnf v = find(id);
        if (v.getState() == VnfState.TERMINATED || v.getState().isTransitional()) {
            throw ApiException.invalidState("VNF cannot be configured in state " + v.getState());
        }
        StringBuilder changes = new StringBuilder();
        if (req.description() != null) {
            v.setDescription(req.description());
            changes.append("description ");
        }
        boolean policyChanged = false;
        if (req.defaultAction() != null && req.defaultAction() != v.getDefaultAction()) {
            v.setDefaultAction(req.defaultAction());
            v.setRulesRevision(v.getRulesRevision() + 1);
            v.setSyncStatus(VnfEnums.SyncStatus.PENDING);
            policyChanged = true;
            changes.append("defaultAction=").append(req.defaultAction());
        }
        vnfs.save(v);
        recordEvent(id, VnfEnums.Operation.CONFIGURE, v.getState(), v.getState(), "SUCCEEDED", changes.toString().trim());
        audit.success(AuditAction.VNF_CONFIGURED, "VNF", "VNF", id, "Configured " + v.getName() + ": " + changes);
        if (policyChanged && v.getState() == VnfState.ACTIVE) {
            publisher.publishEvent(new VnfEvents.Activated(id, false, null, null, null));
        }
        return VnfDtos.VnfDto.from(v);
    }

    @Transactional
    public void delete(Long id) {
        Vnf v = find(id);
        boolean deletable = v.getState() == VnfState.CREATED || v.getState() == VnfState.TERMINATED
                || (v.getState() == VnfState.FAILED && v.getExternalId() == null);
        if (!deletable) {
            throw ApiException.invalidState("Terminate the VNF before deleting it (current state " + v.getState() + ")");
        }
        vnfs.delete(v);
        audit.success(AuditAction.VNF_DELETED, "VNF", "VNF", id, "Deleted VNF record " + v.getName());
    }

    // ------------------------------------------------------------------ lifecycle operations

    public VnfDtos.VnfDto deploy(Long id) {
        final String[] netconfPassword = {null};
        return run(id, VnfEnums.Operation.DEPLOY, EnumSet.of(VnfState.CREATED, VnfState.FAILED),
                VnfState.INSTANTIATING, VnfState.ACTIVE, AuditAction.VNF_DEPLOYED,
                (vnf) -> {
                    if (vnf.getExternalId() != null) {
                        // FAILED with leftovers: make sure no orphan remains before re-instantiating
                        orchestrators.forType(vnf.getOrchestrator()).terminate(vnf);
                    }
                    if (props.orchestrator().docker().netconfEnabled() && cipher.available()
                            && vnf.getOrchestrator() == VnfEnums.OrchestratorType.DOCKER) {
                        netconfPassword[0] = cipher.randomSecret(24);
                    }
                    var d = orchestrators.forType(vnf.getOrchestrator())
                            .instantiate(vnf, new VnfOrchestrator.DeployOptions(netconfPassword[0]));
                    return new Outcome(d.message(), v -> {
                        v.setExternalId(d.externalId());
                        v.setMgmtIp(d.mgmtIp());
                        if (d.lanIp() != null) v.setLanIp(d.lanIp());
                        if (d.wanIp() != null) v.setWanIp(d.wanIp());
                        v.setAgentUrl(d.agentUrl());
                        v.setHealth(VnfEnums.Health.HEALTHY);
                        v.setAppliedRevision(0);
                        v.setSyncStatus(VnfEnums.SyncStatus.PENDING);
                    }, new VnfEvents.Activated(vnf.getId(), true, d.netconfHost(), d.netconfPort(), netconfPassword[0]));
                });
    }

    public VnfDtos.VnfDto start(Long id) {
        return run(id, VnfEnums.Operation.START, EnumSet.of(VnfState.STOPPED, VnfState.FAILED),
                VnfState.STARTING, VnfState.ACTIVE, AuditAction.VNF_STARTED, vnf -> {
                    requireDeployed(vnf);
                    orchestrators.forType(vnf.getOrchestrator()).start(vnf);
                    return new Outcome("Started; agent healthy", v -> v.setHealth(VnfEnums.Health.HEALTHY),
                            new VnfEvents.Activated(vnf.getId(), false, null, null, null));
                });
    }

    public VnfDtos.VnfDto stop(Long id) {
        return run(id, VnfEnums.Operation.STOP, EnumSet.of(VnfState.ACTIVE), VnfState.STOPPING, VnfState.STOPPED,
                AuditAction.VNF_STOPPED, vnf -> {
                    orchestrators.forType(vnf.getOrchestrator()).stop(vnf);
                    return new Outcome("Stopped (confirmed by orchestrator)", v -> {
                        v.setHealth(VnfEnums.Health.UNKNOWN);
                        v.setCpuPercent(null);
                        v.setMemoryPercent(null);
                        v.setThroughputBps(null);
                    }, null);
                });
    }

    public VnfDtos.VnfDto restart(Long id) {
        return run(id, VnfEnums.Operation.RESTART, EnumSet.of(VnfState.ACTIVE), VnfState.STARTING, VnfState.ACTIVE,
                AuditAction.VNF_RESTARTED, vnf -> {
                    orchestrators.forType(vnf.getOrchestrator()).restart(vnf);
                    return new Outcome("Restarted; agent healthy", v -> v.setHealth(VnfEnums.Health.HEALTHY),
                            new VnfEvents.Activated(vnf.getId(), false, null, null, null));
                });
    }

    public VnfDtos.VnfDto terminate(Long id) {
        return run(id, VnfEnums.Operation.TERMINATE,
                EnumSet.of(VnfState.CREATED, VnfState.ACTIVE, VnfState.STOPPED, VnfState.FAILED),
                VnfState.TERMINATING, VnfState.TERMINATED, AuditAction.VNF_TERMINATED, vnf -> {
                    if (vnf.getExternalId() != null) {
                        orchestrators.forType(vnf.getOrchestrator()).terminate(vnf);
                    }
                    return new Outcome("Resources released", v -> {
                        v.setExternalId(null);
                        v.setAgentUrl(null);
                        v.setMgmtIp(null);
                        v.setHealth(VnfEnums.Health.UNKNOWN);
                        v.setSyncStatus(VnfEnums.SyncStatus.UNKNOWN);
                        v.setAppliedRevision(0);
                        v.setCpuPercent(null);
                        v.setMemoryPercent(null);
                        v.setMemoryUsageMb(null);
                        v.setThroughputBps(null);
                    }, new VnfEvents.Terminated(vnf.getId()));
                });
    }

    /** Scaling keeps the VNF ACTIVE; executed synchronously because both lab and OSM confirm in-call. */
    public VnfDtos.VnfDto scale(Long id, VnfDtos.ScaleVnfRequest req) {
        Vnf v = tx.execute(s -> find(id));
        if (v.getState() != VnfState.ACTIVE) throw ApiException.invalidState("Only ACTIVE VNFs can be scaled");
        BigDecimal cpu = req.cpuLimit() == null ? v.getCpuLimit() : req.cpuLimit();
        int mem = req.memoryLimitMb() == null ? v.getMemoryLimitMb() : req.memoryLimitMb();
        String detail = req.direction() + " cpu=" + cpu + " memMiB=" + mem;
        try {
            orchestrators.forType(v.getOrchestrator()).scale(v, cpu, mem, req.direction());
        } catch (RuntimeException ex) {
            tx.executeWithoutResult(s -> recordEvent(id, VnfEnums.Operation.SCALE, VnfState.ACTIVE, VnfState.ACTIVE,
                    "FAILED", detail + ": " + ex.getMessage()));
            audit.record(AuditAction.VNF_SCALED, "VNF", "VNF", id, false, detail + ": " + ex.getMessage());
            throw ex;
        }
        return tx.execute(s -> {
            Vnf fresh = find(id);
            if (req.direction() == VnfOrchestrator.ScaleDirection.VERTICAL) {
                fresh.setCpuLimit(cpu);
                fresh.setMemoryLimitMb(mem);
            }
            vnfs.save(fresh);
            recordEvent(id, VnfEnums.Operation.SCALE, VnfState.ACTIVE, VnfState.ACTIVE, "SUCCEEDED", detail);
            audit.success(AuditAction.VNF_SCALED, "VNF", "VNF", id, "Scaled " + fresh.getName() + ": " + detail);
            return VnfDtos.VnfDto.from(fresh);
        });
    }

    // ------------------------------------------------------------------ engine

    record Outcome(String message, java.util.function.Consumer<Vnf> apply, Object event) {
    }

    private VnfDtos.VnfDto run(Long id, VnfEnums.Operation op, Set<VnfState> allowedFrom, VnfState transitional,
                               VnfState success, String auditAction, Function<Vnf, Outcome> work) {
        String actor = CurrentUser.username();
        NexusPrincipal principal = CurrentUser.principal().orElse(null);

        // Phase 1: validate + move to transitional state (committed before any infrastructure call)
        Vnf snapshot = tx.execute(s -> {
            Vnf v = find(id);
            if (!allowedFrom.contains(v.getState()) || !v.getState().canTransitionTo(transitional)) {
                throw ApiException.invalidState("Cannot " + op.name().toLowerCase() + " a VNF in state " + v.getState());
            }
            VnfState from = v.getState();
            v.setState(transitional);
            v.setLastErrorTruncated(null);
            vnfs.saveAndFlush(v);
            recordEvent(id, op, from, transitional, "STARTED", op + " requested by " + actor, actor);
            return v;
        });

        // Phase 2: infrastructure work off the request thread
        executor.execute(() -> {
            if (principal != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            }
            try {
                Outcome outcome = work.apply(snapshot);
                Runnable complete = () -> tx.executeWithoutResult(s -> {
                    Vnf v = find(id);
                    outcome.apply().accept(v);
                    v.setState(success);
                    vnfs.save(v);
                    recordEvent(id, op, transitional, success, "SUCCEEDED", outcome.message(), actor);
                    if (outcome.event() != null) publisher.publishEvent(outcome.event());
                });
                try {
                    complete.run();
                } catch (org.springframework.dao.OptimisticLockingFailureException retry) {
                    complete.run(); // concurrent monitor write; infrastructure already confirmed success
                }
                audit.success(auditAction, "VNF", "VNF", id, op + " of " + snapshot.getName() + " succeeded: " + outcome.message());
            } catch (RuntimeException ex) {
                log.warn("VNF {} {} failed: {}", snapshot.getName(), op, ex.getMessage());
                tx.executeWithoutResult(s -> {
                    Vnf v = find(id);
                    v.setState(VnfState.FAILED);
                    v.setHealth(VnfEnums.Health.UNKNOWN);
                    v.setLastErrorTruncated(op + " failed: " + ex.getMessage());
                    vnfs.save(v);
                    recordEvent(id, op, transitional, VnfState.FAILED, "FAILED", ex.getMessage(), actor);
                });
                audit.record(auditAction, "VNF", "VNF", id, false, op + " of " + snapshot.getName() + " failed: " + ex.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
        return VnfDtos.VnfDto.from(snapshot);
    }

    void recordEvent(Long vnfId, VnfEnums.Operation op, VnfState from, VnfState to, String outcome, String message) {
        recordEvent(vnfId, op, from, to, outcome, message, CurrentUser.username());
    }

    void recordEvent(Long vnfId, VnfEnums.Operation op, VnfState from, VnfState to, String outcome, String message,
                     String actor) {
        VnfLifecycleEvent e = new VnfLifecycleEvent();
        e.setVnfId(vnfId);
        e.setOperation(op);
        e.setFromState(from);
        e.setToState(to);
        e.setOutcome(outcome);
        e.setMessage(message == null ? null : (message.length() > 1000 ? message.substring(0, 1000) : message));
        e.setActor(actor);
        e.setOccurredAt(Instant.now());
        events.save(e);
    }

    private Vnf find(Long id) {
        return vnfs.findById(id).orElseThrow(() -> ApiException.notFound("VNF", id));
    }

    private static void requireDeployed(Vnf v) {
        if (v.getExternalId() == null) {
            throw ApiException.invalidState("VNF has never been deployed; use deploy instead of start");
        }
    }

    // ------------------------------------------------------------------ IP helpers

    static String defaultGatewayIp(String cidr) {
        long[] r = cidrRange(cidr);
        return longToIp(r[1] - 1); // last usable address, e.g. 10.10.0.254
    }

    static void requireInCidr(String ip, String cidr, String field) {
        long[] r = cidrRange(cidr);
        long v = ipToLong(ip);
        if (v <= r[0] || v >= r[1]) {
            throw ApiException.badRequest(field + " " + ip + " is not a usable host address in " + cidr);
        }
    }

    /** Returns [network, broadcast]. */
    static long[] cidrRange(String cidr) {
        String[] parts = cidr.split("/");
        int prefix = Integer.parseInt(parts[1]);
        long base = ipToLong(parts[0]);
        long mask = prefix == 0 ? 0 : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        long network = base & mask;
        long broadcast = network | (~mask & 0xFFFFFFFFL);
        return new long[]{network, broadcast};
    }

    /** Strict dotted-quad parser (never performs DNS resolution). */
    static long ipToLong(String ip) {
        if (ip == null || !ip.matches(VnfDtos.IPV4)) throw ApiException.badRequest("Invalid IPv4 address: " + ip);
        long v = 0;
        for (String octet : ip.split("\\.")) v = (v << 8) | Integer.parseInt(octet);
        return v;
    }

    static String longToIp(long v) {
        return ((v >> 24) & 0xFF) + "." + ((v >> 16) & 0xFF) + "." + ((v >> 8) & 0xFF) + "." + (v & 0xFF);
    }
}
