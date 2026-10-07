package com.interviewprep.user;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration only ever creates USER accounts, so the first ADMIN is created here at startup from the optional
 * {@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} environment variables. Does nothing unless both are set, and never
 * overwrites an existing account.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            return;
        }
        String email = UserService.normalizeEmail(adminEmail);
        if (userRepository.existsByEmail(email)) {
            return;
        }
        Instant createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        userRepository.save(new AppUser(email, passwordEncoder.encode(adminPassword), Role.ADMIN, createdAt));
        log.info("Created bootstrap ADMIN account");
    }
}
