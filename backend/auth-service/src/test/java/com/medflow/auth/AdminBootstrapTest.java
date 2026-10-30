package com.medflow.auth;

import com.medflow.auth.domain.Role;
import com.medflow.auth.domain.UserAccount;
import com.medflow.auth.repository.UserAccountRepository;
import com.medflow.auth.service.AdminBootstrap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapTest {
    @Test void createsInitialAdminWithEncodedPassword() throws Exception {
        UserAccountRepository users = mock(UserAccountRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(users.existsByEmail("admin@example.test")).thenReturn(false);
        when(passwords.encode("AdminPass123!")).thenReturn("encoded");
        new AdminBootstrap(users, passwords, "ADMIN@EXAMPLE.TEST", "AdminPass123!", "Bootstrap Admin").run(null);
        var account = org.mockito.ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(account.capture());
        assertEquals(Role.ADMIN, account.getValue().getRole());
        assertEquals("admin@example.test", account.getValue().getEmail());
        assertEquals("encoded", account.getValue().getPasswordHash());
    }

    @Test void existingAccountIsNeverOverwritten() throws Exception {
        UserAccountRepository users = mock(UserAccountRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        when(users.existsByEmail("admin@example.test")).thenReturn(true);
        new AdminBootstrap(users, passwords, "admin@example.test", "AdminPass123!", "Bootstrap Admin").run(null);
        verify(users, never()).save(any());
        verify(passwords, never()).encode(any());
    }

    @Test void absentConfigurationDoesNothingAndPartialConfigurationFails() throws Exception {
        UserAccountRepository users = mock(UserAccountRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        new AdminBootstrap(users, passwords, "", "", "Bootstrap Admin").run(null);
        verifyNoInteractions(users, passwords);
        assertThrows(IllegalStateException.class,
                () -> new AdminBootstrap(users, passwords, "admin@example.test", "", "Bootstrap Admin").run(null));
    }
}
