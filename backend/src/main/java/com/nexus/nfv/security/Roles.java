package com.nexus.nfv.security;

/**
 * SpEL expressions for method security. Centralised so the RBAC matrix is defined in one place
 * (documented in docs/SECURITY_DESIGN.md).
 */
public final class Roles {

    /** Any authenticated platform user (read-only views). */
    public static final String ANY = "isAuthenticated()";

    /** Platform administration: users, settings, destructive maintenance. */
    public static final String ADMIN = "hasRole('ADMIN')";

    /** Infrastructure changes: VNF lifecycle, firewall rules, SDN policy, NETCONF. */
    public static final String OPERATE = "hasAnyRole('ADMIN','NETWORK_OPERATOR')";

    /** Security operations: alert triage, audit review, reports. */
    public static final String SECURITY = "hasAnyRole('ADMIN','SECURITY_ANALYST')";

    private Roles() {
    }
}
