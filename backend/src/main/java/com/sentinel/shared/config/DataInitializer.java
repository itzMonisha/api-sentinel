package com.sentinel.shared.config;

import com.sentinel.modules.auth.entity.Role;
import com.sentinel.modules.auth.entity.User;
import com.sentinel.modules.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String defaultEmail = "developer@apisentinel.dev";
        if (userRepository.findByEmail(defaultEmail).isEmpty()) {
            log.info("Bootstrapping initial developer account: {}", defaultEmail);
            User developer = new User(
                    defaultEmail,
                    passwordEncoder.encode("Password123!@#"),
                    "Lead Developer",
                    Role.ROLE_ADMIN
            );
            userRepository.save(developer);
            log.info("Developer account bootstrapped successfully.");
        }
    }
}
