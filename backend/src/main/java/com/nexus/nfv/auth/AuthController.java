package com.nexus.nfv.auth;

import com.nexus.nfv.security.NexusPrincipal;
import com.nexus.nfv.security.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, logout, session management")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and obtain a JWT access token")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return service.login(req);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Revoke the current session")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal NexusPrincipal principal) {
        service.logout(principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearer")
    public AuthDtos.Me me(@AuthenticationPrincipal NexusPrincipal principal) {
        return service.me(principal);
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Change own password (revokes all sessions of the user)")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal NexusPrincipal principal,
                                               @Valid @RequestBody AuthDtos.ChangePasswordRequest req) {
        service.changePassword(principal, req);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "List active sessions across all users")
    public List<AuthDtos.SessionDto> sessions() {
        return service.activeSessions();
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize(Roles.ADMIN)
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Force-revoke a session")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        service.revokeSession(id);
        return ResponseEntity.noContent().build();
    }
}
