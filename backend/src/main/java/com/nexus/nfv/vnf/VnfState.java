package com.nexus.nfv.vnf;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * VNF lifecycle state machine (ETSI NFV-SOL inspired).
 *
 * <pre>
 *  CREATED ──deploy──▶ INSTANTIATING ──ok──▶ ACTIVE ──stop──▶ STOPPING ──▶ STOPPED
 *     │                     │ fail                │  ▲                          │
 *     │                     ▼                     │  └──── STARTING ◀──start────┘
 *     │                  FAILED ◀──── any transitional state on infrastructure error
 *     │                     │
 *     └──────terminate──────┴──▶ TERMINATING ──▶ TERMINATED ──delete──▶ (removed)
 * </pre>
 */
public enum VnfState {
    CREATED,
    INSTANTIATING,
    ACTIVE,
    STOPPING,
    STOPPED,
    STARTING,
    TERMINATING,
    TERMINATED,
    FAILED;

    private static final Map<VnfState, Set<VnfState>> ALLOWED = Map.of(
            CREATED, EnumSet.of(INSTANTIATING, TERMINATING),
            INSTANTIATING, EnumSet.of(ACTIVE, FAILED),
            ACTIVE, EnumSet.of(STOPPING, STARTING, TERMINATING, FAILED, ACTIVE), // STARTING = restart
            STOPPING, EnumSet.of(STOPPED, STARTING, FAILED),
            STOPPED, EnumSet.of(STARTING, TERMINATING, FAILED),
            STARTING, EnumSet.of(ACTIVE, FAILED),
            TERMINATING, EnumSet.of(TERMINATED, FAILED),
            TERMINATED, EnumSet.noneOf(VnfState.class),
            FAILED, EnumSet.of(INSTANTIATING, STARTING, TERMINATING));

    public boolean canTransitionTo(VnfState target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTransitional() {
        return this == INSTANTIATING || this == STOPPING || this == STARTING || this == TERMINATING;
    }

    /** States in which the VNF has (or may have) resources allocated on the infrastructure. */
    public boolean hasInfrastructureResources() {
        return this != CREATED && this != TERMINATED;
    }
}
