package com.interviewprep.shorturl.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.interviewprep.shorturl.ShortUrl;

public record ShortUrlResponse(
        String shortCode,
        String shortUrl,
        String originalUrl,
        LocalDate expiryDate,
        Instant createdAt) {

    public static ShortUrlResponse from(ShortUrl shortUrl, String baseUrl) {
        return new ShortUrlResponse(
                shortUrl.getShortCode(),
                baseUrl + "/" + shortUrl.getShortCode(),
                shortUrl.getOriginalUrl(),
                shortUrl.getExpiryDate(),
                shortUrl.getCreatedAt());
    }
}
