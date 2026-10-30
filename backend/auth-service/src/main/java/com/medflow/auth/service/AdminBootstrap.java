package com.medflow.auth.service;

import com.medflow.auth.domain.Role;
import com.medflow.auth.domain.UserAccount;
import com.medflow.auth.repository.UserAccountRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final String fullName;

    public AdminBootstrap(UserAccountRepository users, PasswordEncoder passwords,
            @Value("${app.bootstrap.admin.email:}") String email,
            @Value("${app.bootstrap.admin.password:}") String password,
            @Value("${app.bootstrap.admin.full-name:MedFlow Administrator}") String fullName) {
        this.users = users;
        this.passwords = passwords;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (!hasEmail && !hasPassword) return;
        if (!hasEmail || !hasPassword)
            throw new IllegalStateException("Both ADMIN_BOOTSTRAP_EMAIL and ADMIN_BOOTSTRAP_PASSWORD must be set");
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(normalizedEmail)) return;
        if (password.length() < 10 || password.length() > 72
                || !password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$"))
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD must satisfy the configured password policy");
        UserAccount admin = new UserAccount();
        admin.setFullName(fullName == null || fullName.isBlank() ? "MedFlow Administrator" : fullName.trim());
        admin.setEmail(normalizedEmail);
        admin.setPasswordHash(passwords.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        users.save(admin);
    }
}
