package com.interviewprep.shorturl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import com.interviewprep.common.exception.ResourceGoneException;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.shorturl.dto.CreateShortUrlRequest;
import com.interviewprep.shorturl.dto.ShortUrlResponse;
import com.interviewprep.shorturl.dto.ShortUrlStatsResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShortUrlService {

    static final int MAX_CODE_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final Clock clock;

    public ShortUrlService(ShortUrlRepository shortUrlRepository, ShortCodeGenerator shortCodeGenerator, Clock clock) {
        this.shortUrlRepository = shortUrlRepository;
        this.shortCodeGenerator = shortCodeGenerator;
        this.clock = clock;
    }

    /**
     * Always creates a new short code, even for a URL that was shortened before, so each link has its own expiry
     * and visit statistics.
     *
     * @param baseUrl the public base address the short URL is built from, e.g. {@code http://localhost:8080}
     */
    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request, String baseUrl) {
        ShortUrl shortUrl = new ShortUrl(generateUniqueCode(), request.url(), request.expiryDate(), Instant.now(clock));
        return ShortUrlResponse.from(shortUrlRepository.save(shortUrl), baseUrl);
    }

    /** Returns the original URL for a redirect and counts the visit. Expired links are not counted. */
    @Transactional
    public String resolveAndCountVisit(String shortCode) {
        ShortUrl shortUrl = getShortUrl(shortCode);
        if (shortUrl.isExpiredOn(LocalDate.now(clock))) {
            throw new ResourceGoneException("Short URL " + shortCode + " has expired");
        }
        shortUrlRepository.incrementVisitCount(shortUrl.getId());
        return shortUrl.getOriginalUrl();
    }

    /** Stats remain available after expiry because they describe past activity. */
    public ShortUrlStatsResponse getStats(String shortCode) {
        return ShortUrlStatsResponse.from(getShortUrl(shortCode));
    }

    private ShortUrl getShortUrl(String shortCode) {
        return shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short URL " + shortCode + " not found"));
    }

    /**
     * Collisions are astronomically unlikely, so a few retries are enough. The unique constraint on
     * {@code short_code} is the real guarantee if two requests race for the same new code.
     */
    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = shortCodeGenerator.generate();
            if (!shortUrlRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique short code after " + MAX_CODE_ATTEMPTS + " attempts");
    }
}
