package com.interviewprep.shorturl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.interviewprep.common.config.ClockConfig;
import com.interviewprep.common.exception.ResourceGoneException;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.common.security.SecurityConfig;
import com.interviewprep.shorturl.dto.CreateShortUrlRequest;
import com.interviewprep.shorturl.dto.ShortUrlResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import({SecurityConfig.class, ClockConfig.class})
@WebMvcTest(ShortUrlController.class)
class ShortUrlControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortUrlService shortUrlService;

    @Test
    void createReturns201WithShortUrl() throws Exception {
        when(shortUrlService.create(any(CreateShortUrlRequest.class), eq("http://localhost")))
                .thenReturn(new ShortUrlResponse("abc1234", "http://localhost/abc1234",
                        "https://example.com/long", null, Instant.parse("2026-10-07T10:00:00Z")));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/long"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/abc1234"))
                .andExpect(jsonPath("$.shortCode").value("abc1234"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost/abc1234"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/long"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not a url",
            "example.com",
            "ftp://example.com/file",
            "javascript:alert(1)",
            "https://exa mple.com",
            "https://example.com/café"})
    void createRejectsInvalidUrls(String url) throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s"}
                                """.formatted(url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a valid http or https URL"));
        verifyNoInteractions(shortUrlService);
    }

    @Test
    void createRejectsMissingUrl() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void createRejectsUrlLongerThan2048Characters() throws Exception {
        String tooLong = "https://example.com/" + "a".repeat(2048);
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s"}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"));
    }

    @Test
    void createRejectsPastExpiryDate() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com", "expiryDate": "2000-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expiryDate"));
    }

    @Test
    void redirectReturns302WithLocationAndNoStore() throws Exception {
        when(shortUrlService.resolveAndCountVisit("abc1234")).thenReturn("https://example.com/long");

        mockMvc.perform(get("/abc1234"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/long"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void redirectUnknownCodeReturns404() throws Exception {
        when(shortUrlService.resolveAndCountVisit("nope123"))
                .thenThrow(new ResourceNotFoundException("Short URL nope123 not found"));

        mockMvc.perform(get("/nope123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Short URL nope123 not found"));
    }

    @Test
    void redirectExpiredCodeReturns410() throws Exception {
        when(shortUrlService.resolveAndCountVisit("old1234"))
                .thenThrow(new ResourceGoneException("Short URL old1234 has expired"));

        mockMvc.perform(get("/old1234"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.detail").value("Short URL old1234 has expired"));
    }

    /**
     * Paths that are not valid short codes are not public routes, so deny-by-default security answers 401 to
     * anonymous callers; an authenticated caller gets 404. Either way the service is never called.
     */
    @Test
    void codesThatAreNotUrlSafeOrTooLongNeverReachTheService() throws Exception {
        mockMvc.perform(get("/abc-123")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/abcdefghi")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/abc-123").with(jwt())).andExpect(status().isNotFound());
        mockMvc.perform(get("/abcdefghi").with(jwt())).andExpect(status().isNotFound());
        verifyNoInteractions(shortUrlService);
    }

    @Test
    void statsUnknownCodeReturns404() throws Exception {
        when(shortUrlService.getStats(anyString()))
                .thenThrow(new ResourceNotFoundException("Short URL nope123 not found"));

        mockMvc.perform(get("/api/urls/nope123/stats"))
                .andExpect(status().isNotFound());
    }
}
