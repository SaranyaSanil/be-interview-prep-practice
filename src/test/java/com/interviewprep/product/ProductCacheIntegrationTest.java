package com.interviewprep.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import com.interviewprep.product.dto.ProductResponse;
import com.interviewprep.product.dto.UpdateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the cache with a Mockito spy around the real repository: every database lookup by id goes through
 * {@code productRepository.findById}, so counting those calls counts the database lookups.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long productId;

    @BeforeEach
    void setUp() {
        productCache().clear();
        productId = productRepository.findAll().get(0).getId();
        clearInvocations(productRepository);
    }

    @Test
    void repeatedLookupsQueryTheDatabaseOnlyOnce() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/products/{id}", productId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(productId));
        }

        verify(productRepository, times(1)).findById(productId);
        assertThat(productCache().get(productId)).isNotNull();
    }

    @Test
    void updateEvictsSoTheNextLookupReturnsNewData() throws Exception {
        mockMvc.perform(get("/api/products/{id}", productId)).andExpect(status().isOk());

        mockMvc.perform(put("/api/products/{id}", productId).with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Renamed", "category": "Books", "price": 12.50, "stock": 3, "rating": 4.5}
                                """))
                .andExpect(status().isOk());
        assertThat(productCache().get(productId)).isNull();

        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.price").value(12.50));
        mockMvc.perform(get("/api/products/{id}", productId))
                .andExpect(jsonPath("$.name").value("Renamed"));

        // 1st GET (miss) + update's own load + 1st GET after update (miss); the last GET is a cache hit.
        verify(productRepository, times(3)).findById(productId);
    }

    @Test
    void deleteEvictsSoTheNextLookupReturns404() throws Exception {
        mockMvc.perform(get("/api/products/{id}", productId)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/{id}", productId).with(admin()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", productId)).andExpect(status().isNotFound());
        assertThat(productCache().get(productId)).isNull();
    }

    @Test
    void evictionIsDeferredUntilTheTransactionCommits() {
        productService.findById(productId);

        transactionTemplate.executeWithoutResult(status -> {
            productService.update(productId, renameTo("Inside transaction"));
            // Not committed yet: evicting now would let a concurrent reader re-cache the old row.
            assertThat(productCache().get(productId)).isNotNull();
        });

        assertThat(productCache().get(productId)).isNull();
        assertThat(productService.findById(productId).name()).isEqualTo("Inside transaction");
    }

    @Test
    void rolledBackUpdateLeavesTheCachedValueInPlace() {
        String originalName = productService.findById(productId).name();

        transactionTemplate.executeWithoutResult(status -> {
            productService.update(productId, renameTo("Rolled back"));
            status.setRollbackOnly();
        });

        ProductResponse cached = productCache().get(productId, ProductResponse.class);
        assertThat(cached).isNotNull();
        assertThat(cached.name()).isEqualTo(originalName);
        assertThat(productRepository.findById(productId).orElseThrow().getName()).isEqualTo(originalName);
    }

    @Test
    void unknownProductIsNotCached() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/products/{id}", 999_999)).andExpect(status().isNotFound());

        verify(productRepository, times(2)).findById(999_999L);
    }

    private Cache productCache() {
        return cacheManager.getCache(ProductService.PRODUCTS_CACHE);
    }

    private static UpdateProductRequest renameTo(String name) {
        return new UpdateProductRequest(name, "Books", new BigDecimal("9.99"), 1, new BigDecimal("4.0"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
