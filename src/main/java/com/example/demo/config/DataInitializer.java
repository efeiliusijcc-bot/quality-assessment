package com.example.demo.config;

import com.example.demo.app.domain.AppUser;
import com.example.demo.app.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedUsers(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            seedUser(appUserRepository, passwordEncoder, "admin", "System Admin", "ADMIN");
            seedUser(appUserRepository, passwordEncoder, "engineer", "Process Engineer", "ENGINEER");
            seedUser(appUserRepository, passwordEncoder, "operator", "Line Operator", "OPERATOR");
        };
    }

    private void seedUser(
        AppUserRepository appUserRepository,
        PasswordEncoder passwordEncoder,
        String username,
        String realName,
        String roleCode
    ) {
        appUserRepository.findByUsername(username).orElseGet(() ->
            appUserRepository.save(new AppUser(username, passwordEncoder.encode("123456"), realName, roleCode))
        );
    }
}
