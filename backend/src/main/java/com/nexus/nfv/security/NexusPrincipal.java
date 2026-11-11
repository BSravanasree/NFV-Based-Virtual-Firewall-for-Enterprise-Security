package com.nexus.nfv.security;

import com.nexus.nfv.user.RoleName;

/** Authenticated caller resolved from a validated, non-revoked JWT. */
public record NexusPrincipal(Long userId, String username, RoleName role, String sessionId) {
}
