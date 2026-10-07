package com.interviewprep.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Application user. Mapped to {@code users} because {@code user} is a reserved word in H2 and other databases.
 */
@Entity
@Table(name = "users")
public class AppUser {

    public static final int EMAIL_MAX_LENGTH = 254;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored trimmed and lower-cased, so the unique constraint is effectively case-insensitive. */
    @Column(nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
    private String email;

    /** BCrypt hash; the plain-text password is never stored. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected AppUser() {
        // required by JPA
    }

    public AppUser(String email, String passwordHash, Role role, Instant createdAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
