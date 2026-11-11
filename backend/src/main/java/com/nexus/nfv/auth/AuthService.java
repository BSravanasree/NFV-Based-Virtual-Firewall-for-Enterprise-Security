package com.nexus.nfv.auth;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.security.CurrentUser;
import com.nexus.nfv.security.JwtService;
import com.nexus.nfv.security.NexusPrincipal;
import com.nexus.nfv.user.User;
import com.nexus.nfv.user.UserRepository;
import com.nexus.nfv.user.UserSession;
import com.nexus.nfv.user.UserSessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository users;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final NexusProperties props;
    /** Used to spend equal BCrypt time for unknown users (mitigates username enumeration). */
    private final String dummyHash;

    public AuthService(UserRepository users, UserSessionRepository sessions, PasswordEncoder encoder,
                       JwtService jwt, AuditService audit, NexusProperties props) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.props = props;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest req) {
        Instant now = Instant.now();
        User user = users.findByUsername(req.username().trim()).orElse(null);
        if (user == null) {
            encoder.matches(req.password(), dummyHash);
            audit.recordAs(null, truncate(req.username()), AuditAction.LOGIN_FAILED, "AUTH", "USER",
                    null, false, "Unknown username");
            throw invalidCredentials();
        }
        if (!user.isEnabled()) {
            audit.recordAs(user.getId(), user.getUsername(), AuditAction.LOGIN_FAILED, "AUTH", "USER",
                    user.getId(), false, "Account disabled");
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            audit.recordAs(user.getId(), user.getUsername(), AuditAction.LOGIN_FAILED, "AUTH", "USER",
                    user.getId(), false, "Account temporarily locked");
            throw new ApiException(HttpStatus.LOCKED, "ACCOUNT_LOCKED",
                    "Account is temporarily locked after repeated failed logins. Try again later.");
        }
        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            String detail = "Bad password (attempt " + attempts + ")";
            if (attempts >= props.security().maxFailedLogins()) {
                user.setLockedUntil(now.plus(Duration.ofMinutes(props.security().lockoutMinutes())));
                user.setFailedLoginAttempts(0);
                detail += "; account locked for " + props.security().lockoutMinutes() + " minutes";
            }
            users.save(user);
            audit.recordAs(user.getId(), user.getUsername(), AuditAction.LOGIN_FAILED, "AUTH", "USER",
                    user.getId(), false, detail);
            throw invalidCredentials();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        users.save(user);

        JwtService.IssuedToken token = jwt.issue(user.getId(), user.getUsername(), user.roleName());
        UserSession s = new UserSession();
        s.setJti(token.jti());
        s.setUser(user);
        s.setIssuedAt(token.issuedAt());
        s.setExpiresAt(token.expiresAt());
        s.setIpAddress(CurrentUser.clientIp().orElse(null));
        s.setUserAgent(CurrentUser.userAgent().orElse(null));
        sessions.save(s);

        audit.recordAs(user.getId(), user.getUsername(), AuditAction.LOGIN, "AUTH", "USER", user.getId(), true,
                "Session " + token.jti().substring(0, 8) + " issued");
        return new AuthDtos.LoginResponse(token.token(), "Bearer", token.expiresAt(), AuthDtos.Me.from(user));
    }

    @Transactional
    public void logout(NexusPrincipal principal) {
        sessions.findByJti(principal.sessionId()).ifPresent(s -> {
            s.setRevokedAt(Instant.now());
            sessions.save(s);
        });
        audit.success(AuditAction.LOGOUT, "AUTH", "USER", principal.userId(), "Session ended by user");
    }

    @Transactional(readOnly = true)
    public AuthDtos.Me me(NexusPrincipal principal) {
        return users.findById(principal.userId()).map(AuthDtos.Me::from)
                .orElseThrow(() -> ApiException.notFound("User", principal.userId()));
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.SessionDto> activeSessions() {
        return sessions.findActive(Instant.now()).stream().map(AuthDtos.SessionDto::from).toList();
    }

    @Transactional
    public void revokeSession(Long sessionId) {
        UserSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session", sessionId));
        s.setRevokedAt(Instant.now());
        sessions.save(s);
        audit.success(AuditAction.SESSION_REVOKED, "AUTH", "SESSION", sessionId,
                "Revoked session of user " + s.getUser().getUsername());
    }

    @Transactional
    public void changePassword(NexusPrincipal principal, AuthDtos.ChangePasswordRequest req) {
        User user = users.findById(principal.userId())
                .orElseThrow(() -> ApiException.notFound("User", principal.userId()));
        if (!encoder.matches(req.currentPassword(), user.getPasswordHash())) {
            audit.record(AuditAction.PASSWORD_CHANGED, "AUTH", "USER", user.getId(), false, "Current password mismatch");
            throw ApiException.badRequest("Current password is incorrect");
        }
        PasswordPolicy.validate(req.newPassword(), user.getUsername());
        user.setPasswordHash(encoder.encode(req.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        users.save(user);
        sessions.revokeAllForUser(user.getId(), Instant.now());
        audit.success(AuditAction.PASSWORD_CHANGED, "AUTH", "USER", user.getId(), "Password changed; all sessions revoked");
    }

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void purgeExpiredSessions() {
        sessions.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(7)));
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }

    private static String truncate(String s) {
        if (s == null) return "unknown";
        String t = s.trim();
        return t.length() > 64 ? t.substring(0, 64) : t;
    }
}
