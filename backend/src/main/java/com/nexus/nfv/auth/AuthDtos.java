package com.nexus.nfv.auth;

import com.nexus.nfv.user.RoleName;
import com.nexus.nfv.user.User;
import com.nexus.nfv.user.UserSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, Me user) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 10, max = 128) String newPassword) {
    }

    public record Me(Long id, String username, String fullName, String email, RoleName role, Instant lastLoginAt) {
        public static Me from(User u) {
            return new Me(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.roleName(), u.getLastLoginAt());
        }
    }

    public record SessionDto(Long id, String username, Instant issuedAt, Instant expiresAt, String ipAddress,
                             String userAgent) {
        public static SessionDto from(UserSession s) {
            return new SessionDto(s.getId(), s.getUser().getUsername(), s.getIssuedAt(), s.getExpiresAt(),
                    s.getIpAddress(), s.getUserAgent());
        }
    }
}
