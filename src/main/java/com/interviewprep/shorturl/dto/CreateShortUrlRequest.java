package com.interviewprep.shorturl.dto;

import java.time.LocalDate;

import com.interviewprep.shorturl.ShortUrl;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Only absolute http/https URLs without whitespace are accepted. This rejects schemes such as {@code javascript:}
 * or {@code data:} that would turn the redirect into an injection vector, and CR/LF that could split headers.
 */
public record CreateShortUrlRequest(
        @NotBlank
        @Size(max = ShortUrl.ORIGINAL_URL_MAX_LENGTH)
        @URL(regexp = "(?i)^https?://\\S+$", message = "must be a valid http or https URL")
        String url,
        @FutureOrPresent LocalDate expiryDate) {
}
