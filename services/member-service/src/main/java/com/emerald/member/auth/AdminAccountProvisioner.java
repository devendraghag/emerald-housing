package com.emerald.member.auth;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminAccountProvisioner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminAccountProvisioner.class);
    @org.springframework.beans.factory.annotation.Autowired private AccessRegistry registry;
    private final AdminAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminAccountProvisioner(AdminAccountRepository accounts, PasswordEncoder passwordEncoder,
            @Value("${emerald.security.provisioning.username:}") String username,
            @Value("${emerald.security.provisioning.password:}") String password) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) {
            log.info("No initial ADMIN provisioning credentials supplied; no account was created");
            return;
        }
        if (username.isBlank() || password.length() < 14
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Initial ADMIN provisioning requires a username and a password of 14 to 72 UTF-8 bytes");
        }
        int inserted = accounts.insertIfMissing(UUID.randomUUID(), username,
                passwordEncoder.encode(password), Instant.now());
        if (registry != null) {
            registry.database().update("INSERT INTO account_roles(account_id,role_code) SELECT id,role FROM admin_users WHERE username=? ON CONFLICT DO NOTHING",username);
        }
        if (inserted == 1) log.info("Initial ADMIN account was provisioned");
        else log.info("Initial ADMIN username already exists; its password was not changed");
    }
}
