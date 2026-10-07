package com.interviewprep.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-15T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdminWhenConfiguredAndMissing() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("s3cret-pass")).thenReturn("hashed");

        initializer(" Admin@Example.com ", "s3cret-pass").run(null);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void doesNothingWhenNotConfigured() {
        initializer("", "").run(null);
        initializer("admin@example.com", "").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void neverOverwritesAnExistingAccount() {
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(true);

        initializer("admin@example.com", "s3cret-pass").run(null);

        verify(userRepository, never()).save(any());
    }

    private AdminAccountInitializer initializer(String email, String password) {
        return new AdminAccountInitializer(userRepository, passwordEncoder, CLOCK, email, password);
    }
}
