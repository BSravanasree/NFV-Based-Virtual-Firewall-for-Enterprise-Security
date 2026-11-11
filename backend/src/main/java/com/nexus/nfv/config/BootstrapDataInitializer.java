package com.nexus.nfv.config;

import com.nexus.nfv.auth.PasswordPolicy;
import com.nexus.nfv.user.Role;
import com.nexus.nfv.user.RoleName;
import com.nexus.nfv.user.RoleRepository;
import com.nexus.nfv.user.User;
import com.nexus.nfv.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Creates the first administrator from NEXUS_ADMIN_USERNAME / NEXUS_ADMIN_PASSWORD when the user table is
 * empty. Optional demo operator/analyst accounts are created only when NEXUS_DEMO_USERS_PASSWORD is set.
 * No credential is ever hard-coded or logged.
 */
@Component
public class BootstrapDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDataInitializer.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final NexusProperties props;

    public BootstrapDataInitializer(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                                    NexusProperties props) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        NexusProperties.Bootstrap b = props.security().bootstrap();
        if (users.count() == 0) {
            if (b.adminPassword() == null || b.adminPassword().isBlank()) {
                log.error("No users exist and NEXUS_ADMIN_PASSWORD is not set. Set it and restart to create the "
                        + "initial administrator account '{}'.", b.adminUsername());
            } else {
                PasswordPolicy.validate(b.adminPassword(), b.adminUsername());
                create(b.adminUsername(), "Platform Administrator", b.adminEmail(), RoleName.ADMIN, b.adminPassword());
                log.info("Created initial administrator account '{}'", b.adminUsername());
            }
        }
        if (b.demoUsersPassword() != null && !b.demoUsersPassword().isBlank()) {
            if (!users.existsByUsername("operator")) {
                create("operator", "Network Operator", "operator@nexus.local", RoleName.NETWORK_OPERATOR,
                        b.demoUsersPassword());
                log.info("Created demo account 'operator' (NETWORK_OPERATOR)");
            }
            if (!users.existsByUsername("analyst")) {
                create("analyst", "Security Analyst", "analyst@nexus.local", RoleName.SECURITY_ANALYST,
                        b.demoUsersPassword());
                log.info("Created demo account 'analyst' (SECURITY_ANALYST)");
            }
        }
    }

    private void create(String username, String fullName, String email, RoleName roleName, String password) {
        Role role = roles.findByName(roleName).orElseThrow();
        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash(encoder.encode(password));
        u.setPasswordChangedAt(Instant.now());
        u.setEnabled(true);
        users.save(u);
    }
}
