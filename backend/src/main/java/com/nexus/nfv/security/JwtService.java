package com.nexus.nfv.security;

import com.nexus.nfv.config.NexusProperties;
import com.nexus.nfv.user.RoleName;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/** Issues and validates HS256 JWTs. Revocation is enforced separately via user_sessions. */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String ISSUER = "nexus-nfv";

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(NexusProperties props) {
        String secret = props.security().jwtSecret();
        if (secret == null || secret.isBlank()) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            this.key = Keys.hmacShaKeyFor(random);
            log.warn("NEXUS_JWT_SECRET is not set: generated an ephemeral signing key. "
                    + "All sessions are invalidated on restart. Set NEXUS_JWT_SECRET for any shared deployment.");
        } else {
            byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("NEXUS_JWT_SECRET must be at least 32 bytes");
            }
            this.key = Keys.hmacShaKeyFor(bytes);
        }
        this.ttl = Duration.ofMinutes(Math.max(5, props.security().jwtTtlMinutes()));
    }

    public IssuedToken issue(Long userId, String username, RoleName role) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(username)
                .id(jti)
                .claim("uid", userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, now, exp);
    }

    public Optional<NexusPrincipal> parse(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long uid = c.get("uid", Number.class).longValue();
            RoleName role = RoleName.valueOf(c.get("role", String.class));
            return Optional.of(new NexusPrincipal(uid, c.getSubject(), role, c.getId()));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return ttl;
    }

    public record IssuedToken(String token, String jti, Instant issuedAt, Instant expiresAt) {
    }
}
