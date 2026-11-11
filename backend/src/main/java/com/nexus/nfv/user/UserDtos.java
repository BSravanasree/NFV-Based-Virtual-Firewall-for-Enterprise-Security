package com.nexus.nfv.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                    message = "may contain letters, digits, '.', '_' and '-' only") String username,
            @NotBlank @Size(max = 128) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotNull RoleName role,
            @NotBlank @Size(min = 10, max = 128) String password) {
    }

    public record UpdateUserRequest(
            @Size(max = 128) String fullName,
            @Email @Size(max = 160) String email,
            RoleName role,
            Boolean enabled,
            @Size(min = 10, max = 128) String password,
            Boolean unlock) {
    }

    public record UserDto(Long id, String username, String fullName, String email, RoleName role, boolean enabled,
                          boolean locked, Instant lastLoginAt, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.roleName(),
                    u.isEnabled(), u.isLocked(Instant.now()), u.getLastLoginAt(), u.getCreatedAt());
        }
    }
}
