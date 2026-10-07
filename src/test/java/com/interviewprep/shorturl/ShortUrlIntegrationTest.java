package com.interviewprep.shorturl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.interviewprep.shorturl.dto.CreateShortUrlRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end through Controller → Service → Repository → H2, with a fixed clock so dates are deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ShortUrlIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2030, 1, 15);
    private static final Instant NOW = TODAY.atStartOfDay().toInstant(ZoneOffset.UTC);

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortUrlService shortUrlService;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @BeforeEach
    void cleanDatabase() {
        shortUrlRepository.deleteAll();
    }

    @Test
    void shortenRedirectAndReportStats() throws Exception {
        String body = mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/some/very/long/path?q=1", "expiryDate": "%s"}
                                """.formatted(TODAY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.shortCode");
        assertThat(code).matches("[A-Za-z0-9]{1,8}");
        assertThat((String) JsonPath.read(body, "$.shortUrl")).isEqualTo("http://localhost/" + code);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/{code}", code))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", "https://example.com/some/very/long/path?q=1"));
        }

        mockMvc.perform(get("/api/urls/{code}/stats", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/some/very/long/path?q=1"))
                .andExpect(jsonPath("$.visitCount").value(3))
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()));
    }

    @Test
    void shorteningTheSameUrlTwiceCreatesTwoIndependentCodes() {
        String first = shortUrlService.create(new CreateShortUrlRequest("https://example.com", null), "").shortCode();
        String second = shortUrlService.create(new CreateShortUrlRequest("https://example.com", null), "").shortCode();

        assertThat(first).isNotEqualTo(second);
        shortUrlService.resolveAndCountVisit(first);
        assertThat(shortUrlService.getStats(first).visitCount()).isEqualTo(1);
        assertThat(shortUrlService.getStats(second).visitCount()).isZero();
    }

    @Test
    void expiredCodeReturns410AndIsNotCounted() throws Exception {
        shortUrlRepository.save(new ShortUrl("old1234", "https://example.com", TODAY.minusDays(1), NOW));

        mockMvc.perform(get("/old1234")).andExpect(status().isGone());

        assertThat(shortUrlService.getStats("old1234").visitCount()).isZero();
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/nope123")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/urls/nope123/stats")).andExpect(status().isNotFound());
    }

    /**
     * All threads are released at once by a latch to maximise overlap. A read-modify-write implementation
     * (load entity, visitCount + 1, save) loses updates here; the atomic UPDATE must count every visit.
     */
    @Test
    void concurrentVisitsAreAllCounted() throws Exception {
        String code = shortUrlService.create(new CreateShortUrlRequest("https://example.com", null), "").shortCode();
        int threads = 20;
        int visitsPerThread = 25;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int v = 0; v < visitsPerThread; v++) {
                        shortUrlService.resolveAndCountVisit(code);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(shortUrlService.getStats(code).visitCount()).isEqualTo(threads * visitsPerThread);
    }
}
