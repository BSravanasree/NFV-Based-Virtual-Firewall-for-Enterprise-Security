package com.nexus.nfv.netconf;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.firewall.FirewallRuleRepository;
import com.nexus.nfv.netconf.client.NetconfSession;
import com.nexus.nfv.security.CredentialCipher;
import com.nexus.nfv.security.CurrentUser;
import com.nexus.nfv.vnf.Vnf;
import com.nexus.nfv.vnf.VnfEvents;
import com.nexus.nfv.vnf.VnfRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * NETCONF configuration management of firewall VNFs (Netopeer2/sysrepo inside the VNF image).
 * Push workflow when :candidate is supported: lock → edit-config(candidate, replace) → validate → commit → unlock;
 * otherwise edit-config(running) with test-then-set + rollback-on-error. Every push/rollback is versioned.
 */
@Service
public class NetconfService {

    private static final Logger log = LoggerFactory.getLogger(NetconfService.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    static final String CANDIDATE = "urn:ietf:params:netconf:capability:candidate";
    static final String VALIDATE = "urn:ietf:params:netconf:capability:validate";

    private final NetconfDeviceRepository devices;
    private final NetconfConfigVersionRepository versions;
    private final VnfRepository vnfs;
    private final FirewallRuleRepository rules;
    private final CredentialCipher cipher;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public NetconfService(NetconfDeviceRepository devices, NetconfConfigVersionRepository versions,
                          VnfRepository vnfs, FirewallRuleRepository rules, CredentialCipher cipher,
                          AuditService audit, PlatformTransactionManager txm) {
        this.devices = devices;
        this.versions = versions;
        this.vnfs = vnfs;
        this.rules = rules;
        this.cipher = cipher;
        this.audit = audit;
        this.tx = new TransactionTemplate(txm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ------------------------------------------------------------------ device registry

    public List<NetconfDtos.DeviceDto> list() {
        return devices.findAll().stream().map(NetconfService::toDto).toList();
    }

    public NetconfDtos.DeviceDto get(Long id) {
        return toDto(device(id));
    }

    public NetconfDtos.DeviceDto create(NetconfDtos.DeviceRequest req) {
        if (devices.existsByName(req.name())) throw ApiException.conflict("Device name already exists");
        if (req.vnfId() != null && !vnfs.existsById(req.vnfId())) throw ApiException.notFound("VNF", req.vnfId());
        NetconfDevice d = new NetconfDevice();
        d.setName(req.name());
        d.setHost(req.host());
        d.setPort(req.port() == null ? 830 : req.port());
        d.setUsername(req.username());
        d.setPasswordEnc(cipher.encrypt(req.password()));
        d.setVnfId(req.vnfId());
        d.setStatus("UNKNOWN");
        d.setCreatedBy(CurrentUser.username());
        tx.executeWithoutResult(s -> devices.save(d));
        audit.success(AuditAction.NETCONF_DEVICE_CREATED, "NETCONF", "NETCONF_DEVICE", d.getId(),
                "Registered NETCONF device " + d.getName() + " (" + d.getHost() + ":" + d.getPort() + ")");
        return toDto(d);
    }

    public void delete(Long id) {
        NetconfDevice d = device(id);
        tx.executeWithoutResult(s -> devices.deleteById(id));
        audit.success(AuditAction.NETCONF_DEVICE_DELETED, "NETCONF", "NETCONF_DEVICE", id, "Removed " + d.getName());
    }

    public NetconfDtos.DeviceDto resetHostKey(Long id) {
        NetconfDevice d = device(id);
        d.setHostKeyFingerprint(null);
        tx.executeWithoutResult(s -> devices.save(d));
        audit.success(AuditAction.NETCONF_DEVICE_CREATED, "NETCONF", "NETCONF_DEVICE", id, "Pinned host key reset for " + d.getName());
        return toDto(d);
    }

    /** Opens a session, records capabilities and pins the host key (TOFU). */
    public NetconfDtos.DeviceDto test(Long id) {
        withSession(id, session -> null);
        return toDto(device(id));
    }

    // ------------------------------------------------------------------ configuration

    public NetconfDtos.RunningConfig running(Long id) {
        String xml = withSession(id, s -> s.getConfig("running", FirewallYangCodec.FILTER));
        return new NetconfDtos.RunningConfig(id, "running", xml, FirewallYangCodec.checksum(xml),
                FirewallYangCodec.ruleCount(xml), Instant.now());
    }

    /** Stores the current running configuration as a version (snapshot). */
    public NetconfDtos.VersionDto snapshot(Long id) {
        NetconfDtos.RunningConfig rc = running(id);
        return NetconfDtos.VersionDto.from(saveVersion(id, "RETRIEVE", "running", rc.configXml(), "RETRIEVED",
                "Snapshot of running datastore (" + rc.ruleCount() + " rules)"));
    }

    /** Desired configuration generated from the linked VNF's firewall policy (YANG-encoded). */
    public String desiredXml(Long id) {
        NetconfDevice d = device(id);
        if (d.getVnfId() == null) throw ApiException.invalidState("Device is not linked to a firewall VNF");
        Vnf v = vnfs.findById(d.getVnfId()).orElseThrow(() -> ApiException.notFound("VNF", d.getVnfId()));
        return FirewallYangCodec.encode(v, rules.findByVnfIdOrderByPriorityAsc(v.getId()));
    }

    /** Validates the desired configuration on the device's candidate datastore without committing. */
    public NetconfDtos.OperationResult validate(Long id) {
        String xml = desiredXml(id);
        return withSession(id, s -> {
            if (!s.supports(CANDIDATE)) {
                return new NetconfDtos.OperationResult(false,
                        "Device lacks :candidate; validation would require modifying running", null);
            }
            s.lock("candidate");
            try {
                s.editConfig("candidate", xml, "merge");
                s.validate("candidate");
                return new NetconfDtos.OperationResult(true, "Candidate configuration is valid against the device YANG schema", null);
            } finally {
                safe(s::discardChanges);
                safe(() -> s.unlock("candidate"));
            }
        });
    }

    public NetconfDtos.OperationResult push(Long id) {
        String xml = desiredXml(id);
        return apply(id, xml, "PUSH", AuditAction.NETCONF_CONFIG_PUSHED, "Pushed firewall policy");
    }

    public NetconfDtos.OperationResult rollback(Long id, Long versionId) {
        NetconfConfigVersion target = versions.findById(versionId)
                .filter(v -> v.getDeviceId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Configuration version", versionId));
        if ("FAILED".equals(target.getStatus())) throw ApiException.invalidState("Cannot roll back to a failed version");
        return apply(id, target.getConfigXml(), "ROLLBACK", AuditAction.NETCONF_CONFIG_ROLLBACK,
                "Rolled back to version " + target.getVersionNo());
    }

    private NetconfDtos.OperationResult apply(Long id, String xml, String operation, String auditAction, String what) {
        try {
            String datastore = withSession(id, s -> {
                if (s.supports(CANDIDATE)) {
                    s.lock("candidate");
                    try {
                        s.discardChanges();
                        s.editConfig("candidate", xml, "merge");
                        if (s.supports(VALIDATE)) s.validate("candidate");
                        s.commit();
                    } catch (RuntimeException ex) {
                        safe(s::discardChanges);
                        throw ex;
                    } finally {
                        safe(() -> s.unlock("candidate"));
                    }
                    return "candidate";
                }
                s.editConfig("running", xml, "merge");
                return "running";
            });
            NetconfConfigVersion v = saveVersion(id, operation, datastore, xml, "APPLIED",
                    what + " via " + datastore + (datastore.equals("candidate") ? " + commit" : ""));
            audit.success(auditAction, "NETCONF", "NETCONF_DEVICE", id, what + " (version " + v.getVersionNo() + ")");
            return new NetconfDtos.OperationResult(true, what + " – committed by device", NetconfDtos.VersionDto.from(v));
        } catch (RuntimeException ex) {
            NetconfConfigVersion v = saveVersion(id, operation, "candidate", xml, "FAILED", ex.getMessage());
            audit.record(auditAction, "NETCONF", "NETCONF_DEVICE", id, false, what + " failed: " + ex.getMessage());
            return new NetconfDtos.OperationResult(false, ex.getMessage(), NetconfDtos.VersionDto.from(v));
        }
    }

    public NetconfDtos.SyncReport sync(Long id) {
        String desired;
        try {
            desired = FirewallYangCodec.checksum(desiredXml(id));
        } catch (ApiException ex) {
            desired = null;
        }
        var lastApplied = versions.findFirstByDeviceIdAndStatusOrderByVersionNoDesc(id, "APPLIED");
        try {
            NetconfDtos.RunningConfig rc = running(id);
            String status;
            String detail;
            if (desired != null) {
                status = rc.checksum().equals(desired) ? "IN_SYNC" : "DRIFT";
                detail = status.equals("IN_SYNC") ? "Running datastore matches the platform's desired policy"
                        : "Running datastore differs from the platform's desired policy (" + rc.ruleCount() + " rules on device)";
            } else if (lastApplied.isPresent()) {
                status = rc.checksum().equals(lastApplied.get().getChecksum()) ? "IN_SYNC" : "DRIFT";
                detail = "Compared with last applied version " + lastApplied.get().getVersionNo();
            } else {
                status = "NO_BASELINE";
                detail = "No desired policy or applied version to compare with";
            }
            return new NetconfDtos.SyncReport(id, status, rc.checksum(), desired,
                    lastApplied.map(NetconfConfigVersion::getChecksum).orElse(null),
                    lastApplied.map(NetconfConfigVersion::getVersionNo).orElse(null), detail);
        } catch (RuntimeException ex) {
            return new NetconfDtos.SyncReport(id, "UNKNOWN", null, desired,
                    lastApplied.map(NetconfConfigVersion::getChecksum).orElse(null),
                    lastApplied.map(NetconfConfigVersion::getVersionNo).orElse(null), ex.getMessage());
        }
    }

    public List<NetconfDtos.VersionDto> history(Long id) {
        device(id);
        return versions.findByDeviceIdOrderByVersionNoDesc(id).stream().map(NetconfDtos.VersionDto::from).toList();
    }

    public NetconfDtos.VersionContent version(Long id, Long versionId) {
        NetconfConfigVersion v = versions.findById(versionId).filter(x -> x.getDeviceId().equals(id))
                .orElseThrow(() -> ApiException.notFound("Configuration version", versionId));
        return new NetconfDtos.VersionContent(NetconfDtos.VersionDto.from(v), v.getConfigXml());
    }

    // ------------------------------------------------------------------ VNF integration

    /** Fresh lab deployments come with a generated NETCONF credential: register the device automatically. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVnfActivated(VnfEvents.Activated e) {
        if (!e.freshDeployment() || e.netconfPassword() == null || e.netconfHost() == null) return;
        try {
            tx.executeWithoutResult(s -> {
                Vnf v = vnfs.findById(e.vnfId()).orElseThrow();
                NetconfDevice d = devices.findFirstByVnfId(v.getId()).orElseGet(NetconfDevice::new);
                if (d.getId() == null) {
                    String name = v.getName() + "-netconf";
                    d.setName(devices.existsByName(name) ? name + "-" + v.getId() : name);
                    d.setCreatedBy("system");
                }
                d.setHost(e.netconfHost());
                d.setPort(e.netconfPort() == null ? 830 : e.netconfPort());
                d.setUsername("nexus");
                d.setPasswordEnc(cipher.encrypt(e.netconfPassword()));
                d.setVnfId(v.getId());
                d.setStatus("UNKNOWN");
                d.setHostKeyFingerprint(null); // new VNF instance = new host key
                devices.save(d);
            });
            log.info("Registered NETCONF endpoint for VNF {}", e.vnfId());
        } catch (RuntimeException ex) {
            log.warn("Could not auto-register NETCONF device for VNF {}: {}", e.vnfId(), ex.getMessage());
        }
    }

    /**
     * A terminated VNF's NETCONF endpoint no longer exists: remove the device the platform registered for it
     * (operator-registered devices are kept, only detached by the foreign key).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVnfTerminated(VnfEvents.Terminated e) {
        try {
            tx.executeWithoutResult(s -> devices.findFirstByVnfId(e.vnfId())
                    .filter(d -> "system".equals(d.getCreatedBy()))
                    .ifPresent(d -> {
                        devices.delete(d);
                        log.info("Removed auto-registered NETCONF device {} of terminated VNF {}", d.getName(), e.vnfId());
                    }));
        } catch (RuntimeException ex) {
            log.warn("Could not remove NETCONF device of terminated VNF {}: {}", e.vnfId(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ helpers

    private <T> T withSession(Long id, Function<NetconfSession, T> fn) {
        NetconfDevice d = device(id);
        String password = cipher.decrypt(d.getPasswordEnc());
        try (NetconfSession s = NetconfSession.open(d.getHost(), d.getPort(), d.getUsername(), password,
                d.getHostKeyFingerprint(), TIMEOUT)) {
            T result = fn.apply(s);
            tx.executeWithoutResult(st -> devices.findById(id).ifPresent(x -> {
                x.setStatus("CONNECTED");
                x.setLastConnectedAt(Instant.now());
                x.setLastError(null);
                x.setCapabilities(String.join("\n", s.capabilities()));
                if (x.getHostKeyFingerprint() == null) x.setHostKeyFingerprint(s.hostKeyFingerprint());
                devices.save(x);
            }));
            return result;
        } catch (RuntimeException ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            tx.executeWithoutResult(st -> devices.findById(id).ifPresent(x -> {
                x.setStatus(msg.contains("authentication") ? "AUTH_FAILED"
                        : msg.contains("rpc-error") ? "CONNECTED" : "UNREACHABLE");
                x.setLastError(msg.length() > 1000 ? msg.substring(0, 1000) : msg);
                devices.save(x);
            }));
            throw ex;
        }
    }

    private NetconfConfigVersion saveVersion(Long deviceId, String op, String ds, String xml, String status, String msg) {
        return tx.execute(s -> {
            int next = versions.findFirstByDeviceIdOrderByVersionNoDesc(deviceId).map(v -> v.getVersionNo() + 1).orElse(1);
            NetconfConfigVersion v = new NetconfConfigVersion();
            v.setDeviceId(deviceId);
            v.setVersionNo(next);
            v.setOperation(op);
            v.setDatastore(ds);
            v.setConfigXml(xml == null ? "" : xml);
            v.setChecksum(FirewallYangCodec.checksum(xml));
            v.setStatus(status);
            v.setMessage(msg == null ? null : (msg.length() > 1000 ? msg.substring(0, 1000) : msg));
            v.setCreatedBy(CurrentUser.username());
            v.setCreatedAt(Instant.now());
            return versions.save(v);
        });
    }

    private NetconfDevice device(Long id) {
        return devices.findById(id).orElseThrow(() -> ApiException.notFound("NETCONF device", id));
    }

    private static void safe(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ignored) {
            // best-effort cleanup
        }
    }

    static NetconfDtos.DeviceDto toDto(NetconfDevice d) {
        List<String> caps = d.getCapabilities() == null ? List.of()
                : Arrays.stream(d.getCapabilities().split("\n")).filter(c -> !c.isBlank()).toList();
        return new NetconfDtos.DeviceDto(d.getId(), d.getName(), d.getHost(), d.getPort(), d.getUsername(), d.getVnfId(),
                d.getStatus(), caps, d.getHostKeyFingerprint(), d.getLastError(), d.getLastConnectedAt(),
                d.getCreatedBy(), d.getCreatedAt());
    }
}
