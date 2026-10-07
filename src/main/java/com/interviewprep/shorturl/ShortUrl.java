package com.interviewprep.shorturl;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "short_urls")
public class ShortUrl {

    public static final int SHORT_CODE_MAX_LENGTH = 8;
    public static final int ORIGINAL_URL_MAX_LENGTH = 2048;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = SHORT_CODE_MAX_LENGTH)
    private String shortCode;

    @Column(nullable = false, updatable = false, length = ORIGINAL_URL_MAX_LENGTH)
    private String originalUrl;

    /** Inclusive: the link works until the end of this day. Null means it never expires. */
    @Column(updatable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Only ever changed by {@link ShortUrlRepository#incrementVisitCount}, an atomic database update. */
    @Column(nullable = false)
    private long visitCount;

    protected ShortUrl() {
        // required by JPA
    }

    public ShortUrl(String shortCode, String originalUrl, LocalDate expiryDate, Instant createdAt) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.expiryDate = expiryDate;
        this.createdAt = createdAt;
    }

    public boolean isExpiredOn(LocalDate date) {
        return expiryDate != null && date.isAfter(expiryDate);
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVisitCount() {
        return visitCount;
    }
}
