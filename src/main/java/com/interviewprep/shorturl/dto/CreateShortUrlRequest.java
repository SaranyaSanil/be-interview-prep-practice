package com.interviewprep.shorturl.dto;

import java.time.LocalDate;

import com.interviewprep.shorturl.ShortUrl;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Only absolute http/https URLs made of printable ASCII (no spaces) are accepted. This rejects schemes such as
 * {@code javascript:} or {@code data:} that would turn the redirect into an injection vector, CR/LF that could split
 * headers, and raw non-ASCII characters that cannot be sent in a {@code Location} header (clients must
 * percent-encode them, e.g. {@code caf%C3%A9}).
 */
public record CreateShortUrlRequest(
        @NotBlank
        @Size(max = ShortUrl.ORIGINAL_URL_MAX_LENGTH)
        @URL(regexp = "(?i)^https?://[!-~]+$", message = "must be a valid http or https URL")
        String url,
        @FutureOrPresent LocalDate expiryDate) {
}
