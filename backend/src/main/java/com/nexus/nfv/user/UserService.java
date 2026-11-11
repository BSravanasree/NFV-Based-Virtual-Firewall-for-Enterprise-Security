package com.nexus.nfv.user;

import com.nexus.nfv.audit.AuditAction;
import com.nexus.nfv.audit.AuditService;
import com.nexus.nfv.auth.PasswordPolicy;
import com.nexus.nfv.common.exception.ApiException;
import com.nexus.nfv.security.CurrentUser;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final UserSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, RoleRepository roles, UserSessionRepository sessions,
                       PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.sessions = sessions;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserDtos.UserDto> list() {
        return users.findAll(Sort.by("username")).stream().map(UserDtos.UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public UserDtos.UserDto get(Long id) {
        return UserDtos.UserDto.from(find(id));
    }

    @Transactional
    public UserDtos.UserDto create(UserDtos.CreateUserRequest req) {
        String username = req.username().trim().toLowerCase();
        if (users.existsByUsername(username)) throw ApiException.conflict("Username already exists");
        if (users.existsByEmail(req.email().trim().toLowerCase())) throw ApiException.conflict("Email already in use");
        PasswordPolicy.validate(req.password(), username);

        User u = new User();
        u.setUsername(username);
        u.setFullName(req.fullName().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setRole(role(req.role()));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setPasswordChangedAt(Instant.now());
        u.setEnabled(true);
        users.save(u);
        audit.success(AuditAction.USER_CREATED, "USER", "USER", u.getId(),
                "Created user " + username + " with role " + req.role());
        return UserDtos.UserDto.from(u);
    }

    @Transactional
    public UserDtos.UserDto update(Long id, UserDtos.UpdateUserRequest req) {
        User u = find(id);
        boolean self = Objects.equals(CurrentUser.principal().map(p -> p.userId()).orElse(null), id);
        StringBuilder changes = new StringBuilder();

        if (req.fullName() != null && !req.fullName().isBlank()) {
            u.setFullName(req.fullName().trim());
            changes.append("fullName ");
        }
        if (req.email() != null && !req.email().isBlank()) {
            String email = req.email().trim().toLowerCase();
            if (!email.equals(u.getEmail()) && users.existsByEmail(email)) throw ApiException.conflict("Email already in use");
            u.setEmail(email);
            changes.append("email ");
        }
        if (req.role() != null && req.role() != u.roleName()) {
            if (self) throw ApiException.badRequest("Administrators cannot change their own role");
            ensureNotLastAdmin(u);
            u.setRole(role(req.role()));
            sessions.revokeAllForUser(u.getId(), Instant.now());
            changes.append("role=").append(req.role()).append(' ');
        }
        if (req.enabled() != null && req.enabled() != u.isEnabled()) {
            if (self && !req.enabled()) throw ApiException.badRequest("You cannot disable your own account");
            if (!req.enabled()) {
                ensureNotLastAdmin(u);
                sessions.revokeAllForUser(u.getId(), Instant.now());
            }
            u.setEnabled(req.enabled());
            changes.append("enabled=").append(req.enabled()).append(' ');
        }
        if (req.password() != null && !req.password().isBlank()) {
            PasswordPolicy.validate(req.password(), u.getUsername());
            u.setPasswordHash(encoder.encode(req.password()));
            u.setPasswordChangedAt(Instant.now());
            sessions.revokeAllForUser(u.getId(), Instant.now());
            changes.append("password-reset ");
        }
        if (Boolean.TRUE.equals(req.unlock())) {
            u.setLockedUntil(null);
            u.setFailedLoginAttempts(0);
            changes.append("unlocked ");
        }
        users.save(u);
        audit.success(AuditAction.USER_UPDATED, "USER", "USER", u.getId(),
                "Updated " + u.getUsername() + ": " + changes.toString().trim());
        return UserDtos.UserDto.from(u);
    }

    @Transactional
    public void delete(Long id) {
        User u = find(id);
        if (Objects.equals(CurrentUser.principal().map(p -> p.userId()).orElse(null), id)) {
            throw ApiException.badRequest("You cannot delete your own account");
        }
        ensureNotLastAdmin(u);
        users.delete(u);
        audit.success(AuditAction.USER_DELETED, "USER", "USER", id, "Deleted user " + u.getUsername());
    }

    private void ensureNotLastAdmin(User u) {
        if (u.roleName() == RoleName.ADMIN && u.isEnabled()
                && users.countByRoleNameAndEnabledTrue(RoleName.ADMIN) <= 1) {
            throw ApiException.invalidState("The last enabled administrator cannot be removed or demoted");
        }
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }

    private Role role(RoleName name) {
        return roles.findByName(name).orElseThrow(() -> ApiException.badRequest("Unknown role " + name));
    }
}
