package com.nexus.nfv.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/** Static accessors for the current principal and client address. */
public final class CurrentUser {

    public static final String SYSTEM = "system";

    private CurrentUser() {
    }

    public static Optional<NexusPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof NexusPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static String username() {
        return principal().map(NexusPrincipal::username).orElse(SYSTEM);
    }

    public static Optional<String> clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return Optional.ofNullable(req.getRemoteAddr());
        }
        return Optional.empty();
    }

    public static Optional<String> userAgent() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String ua = attrs.getRequest().getHeader("User-Agent");
            return Optional.ofNullable(ua).map(s -> s.length() > 255 ? s.substring(0, 255) : s);
        }
        return Optional.empty();
    }
}
