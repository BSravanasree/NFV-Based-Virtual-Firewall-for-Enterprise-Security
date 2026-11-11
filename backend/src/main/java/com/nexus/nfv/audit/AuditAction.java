package com.nexus.nfv.audit;

/** Canonical audit action identifiers (stable strings – used by filters and reports). */
public final class AuditAction {
    public static final String LOGIN = "USER_LOGIN";
    public static final String LOGIN_FAILED = "USER_LOGIN_FAILED";
    public static final String LOGOUT = "USER_LOGOUT";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    public static final String VNF_CREATED = "VNF_CREATED";
    public static final String VNF_DEPLOYED = "VNF_DEPLOYED";
    public static final String VNF_STARTED = "VNF_STARTED";
    public static final String VNF_STOPPED = "VNF_STOPPED";
    public static final String VNF_RESTARTED = "VNF_RESTARTED";
    public static final String VNF_CONFIGURED = "VNF_CONFIGURED";
    public static final String VNF_SCALED = "VNF_SCALED";
    public static final String VNF_TERMINATED = "VNF_TERMINATED";
    public static final String VNF_DELETED = "VNF_DELETED";

    public static final String FW_RULE_CREATED = "FIREWALL_RULE_CREATED";
    public static final String FW_RULE_UPDATED = "FIREWALL_RULE_UPDATED";
    public static final String FW_RULE_DELETED = "FIREWALL_RULE_DELETED";
    public static final String FW_RULES_REORDERED = "FIREWALL_RULES_REORDERED";
    public static final String FW_POLICY_APPLIED = "FIREWALL_POLICY_APPLIED";

    public static final String NET_POLICY_CREATED = "NETWORK_POLICY_CREATED";
    public static final String NET_POLICY_DELETED = "NETWORK_POLICY_DELETED";
    public static final String FLOW_DELETED = "FLOW_RULE_DELETED";

    public static final String NETCONF_DEVICE_CREATED = "NETCONF_DEVICE_CREATED";
    public static final String NETCONF_DEVICE_DELETED = "NETCONF_DEVICE_DELETED";
    public static final String NETCONF_CONFIG_PUSHED = "NETCONF_CONFIG_PUSHED";
    public static final String NETCONF_CONFIG_ROLLBACK = "NETCONF_CONFIG_ROLLBACK";

    public static final String ALERT_UPDATED = "ALERT_UPDATED";
    public static final String SETTING_CHANGED = "SETTING_CHANGED";
    public static final String REPORT_EXPORTED = "REPORT_EXPORTED";

    private AuditAction() {
    }
}
