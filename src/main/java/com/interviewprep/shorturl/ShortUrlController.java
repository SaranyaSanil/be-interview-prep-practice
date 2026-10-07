package com.interviewprep.shorturl;

import java.net.URI;

import com.interviewprep.shorturl.dto.CreateShortUrlRequest;
import com.interviewprep.shorturl.dto.ShortUrlResponse;
import com.interviewprep.shorturl.dto.ShortUrlStatsResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    public ShortUrlController(ShortUrlService shortUrlService) {
        this.shortUrlService = shortUrlService;
    }

    @PostMapping("/api/urls")
    public ResponseEntity<ShortUrlResponse> create(@Valid @RequestBody CreateShortUrlRequest request) {
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        ShortUrlResponse created = shortUrlService.create(request, baseUrl);
        return ResponseEntity.created(URI.create(created.shortUrl())).body(created);
    }

    @GetMapping("/api/urls/{shortCode}/stats")
    public ShortUrlStatsResponse stats(@PathVariable String shortCode) {
        return shortUrlService.getStats(shortCode);
    }

    /**
     * 302 rather than 301: browsers cache permanent redirects and would skip this endpoint, so repeat visits would
     * not be counted. {@code no-store} stops intermediaries caching it for the same reason. The path pattern only
     * matches Base62 codes of up to 8 characters, so it never shadows {@code /api/**} routes.
     */
    @GetMapping("/{shortCode:[A-Za-z0-9]{1,8}}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
        String originalUrl = shortUrlService.resolveAndCountVisit(shortCode);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, originalUrl)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
