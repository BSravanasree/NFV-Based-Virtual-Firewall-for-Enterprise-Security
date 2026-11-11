package com.nexus.nfv.security;

import com.nexus.nfv.user.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Authenticates requests carrying a Bearer token. A token is accepted only if its signature is
 * valid, it has not expired, and its session (jti) is still active in the database.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserSessionRepository sessions;

    public JwtAuthenticationFilter(JwtService jwtService, UserSessionRepository sessions) {
        this.jwtService = jwtService;
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            jwtService.parse(token)
                    .filter(p -> sessions.findWithUserByJti(p.sessionId())
                            .map(s -> s.isActive(Instant.now()) && s.getUser().isEnabled())
                            .orElse(false))
                    .ifPresent(p -> {
                        var auth = new UsernamePasswordAuthenticationToken(
                                p, null, List.of(new SimpleGrantedAuthority(p.role().authority())));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    });
        }
        chain.doFilter(request, response);
    }
}
