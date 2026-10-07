package com.interviewprep.shorturl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import com.interviewprep.common.exception.ResourceGoneException;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.shorturl.dto.CreateShortUrlRequest;
import com.interviewprep.shorturl.dto.ShortUrlResponse;
import com.interviewprep.shorturl.dto.ShortUrlStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final Instant NOW = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC);
    private static final String BASE_URL = "http://localhost:8080";

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    private ShortUrlService service;

    @BeforeEach
    void setUp() {
        service = new ShortUrlService(shortUrlRepository, shortCodeGenerator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createReturnsShortCodeAndShortUrl() {
        when(shortCodeGenerator.generate()).thenReturn("abc1234");
        when(shortUrlRepository.existsByShortCode("abc1234")).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.create(
                new CreateShortUrlRequest("https://example.com/a/long/path", TODAY.plusDays(1)), BASE_URL);

        assertThat(response.shortCode()).isEqualTo("abc1234");
        assertThat(response.shortUrl()).isEqualTo("http://localhost:8080/abc1234");
        assertThat(response.originalUrl()).isEqualTo("https://example.com/a/long/path");
        assertThat(response.expiryDate()).isEqualTo(TODAY.plusDays(1));
        assertThat(response.createdAt()).isEqualTo(NOW);
    }

    @Test
    void createRetriesWhenGeneratedCodeAlreadyExists() {
        when(shortCodeGenerator.generate()).thenReturn("taken01", "fresh01");
        when(shortUrlRepository.existsByShortCode("taken01")).thenReturn(true);
        when(shortUrlRepository.existsByShortCode("fresh01")).thenReturn(false);
        when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortUrlResponse response = service.create(new CreateShortUrlRequest("https://example.com", null), BASE_URL);

        assertThat(response.shortCode()).isEqualTo("fresh01");
    }

    @Test
    void createFailsAfterMaxAttemptsOfCollisions() {
        when(shortCodeGenerator.generate()).thenReturn("taken01");
        when(shortUrlRepository.existsByShortCode("taken01")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateShortUrlRequest("https://example.com", null), BASE_URL))
                .isInstanceOf(IllegalStateException.class);
        verify(shortUrlRepository, never()).save(any());
    }

    @Test
    void resolveCountsVisitAndReturnsOriginalUrl() {
        ShortUrl shortUrl = storedShortUrl(7L, null);
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(shortUrl));

        assertThat(service.resolveAndCountVisit("abc1234")).isEqualTo("https://example.com");
        verify(shortUrlRepository).incrementVisitCount(7L);
    }

    @Test
    void resolveStillWorksOnTheExpiryDayItself() {
        when(shortUrlRepository.findByShortCode("abc1234")).thenReturn(Optional.of(storedShortUrl(7L, TODAY)));

        assertThat(service.resolveAndCountVisit("abc1234")).isEqualTo("https://example.com");
        verify(shortUrlRepository).incrementVisitCount(7L);
    }

    @Test
    void resolveRejectsExpiredLinkWithoutCountingVisit() {
        when(shortUrlRepository.findByShortCode("abc1234"))
                .thenReturn(Optional.of(storedShortUrl(7L, TODAY.minusDays(1))));

        assertThatThrownBy(() -> service.resolveAndCountVisit("abc1234")).isInstanceOf(ResourceGoneException.class);
        verify(shortUrlRepository, never()).incrementVisitCount(anyLong());
    }

    @Test
    void resolveRejectsUnknownCode() {
        when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAndCountVisit("missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void statsAreAvailableForExpiredLinks() {
        when(shortUrlRepository.findByShortCode("abc1234"))
                .thenReturn(Optional.of(storedShortUrl(7L, TODAY.minusDays(30))));

        ShortUrlStatsResponse stats = service.getStats("abc1234");

        assertThat(stats.originalUrl()).isEqualTo("https://example.com");
        assertThat(stats.visitCount()).isZero();
        assertThat(stats.createdAt()).isEqualTo(NOW);
    }

    private static ShortUrl storedShortUrl(Long id, LocalDate expiryDate) {
        ShortUrl shortUrl = new ShortUrl("abc1234", "https://example.com", expiryDate, NOW);
        ReflectionTestUtils.setField(shortUrl, "id", id);
        return shortUrl;
    }
}
