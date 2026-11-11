package com.nexus.nfv.user;

public enum RoleName {
    ADMIN,
    NETWORK_OPERATOR,
    SECURITY_ANALYST;

    public String authority() {
        return "ROLE_" + name();
    }
}
