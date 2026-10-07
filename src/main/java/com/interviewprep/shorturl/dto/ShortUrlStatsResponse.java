package com.interviewprep.shorturl.dto;

import java.time.Instant;

import com.interviewprep.shorturl.ShortUrl;

public record ShortUrlStatsResponse(String originalUrl, long visitCount, Instant createdAt) {

    public static ShortUrlStatsResponse from(ShortUrl shortUrl) {
        return new ShortUrlStatsResponse(shortUrl.getOriginalUrl(), shortUrl.getVisitCount(), shortUrl.getCreatedAt());
    }
}
