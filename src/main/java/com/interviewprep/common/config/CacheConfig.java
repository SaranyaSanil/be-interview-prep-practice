package com.interviewprep.common.config;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * In-memory Caffeine cache behind Spring's cache abstraction.
 * <ul>
 *   <li>The caching advice runs outside the transactional advice ({@code HIGHEST_PRECEDENCE}). A cache hit
 *       therefore opens no transaction, and for a normal controller → service call {@code @CacheEvict} runs only
 *       after the service's own transaction has committed. Evicting before the commit would let a concurrent read
 *       reload the old row and cache it again.</li>
 *   <li>{@link TransactionAwareCacheManagerProxy} covers the remaining case: when the caller already holds an outer
 *       transaction, it defers the eviction until that outer transaction commits (and skips it on rollback).
 *       Known limitation: inside that same outer transaction, a lookup after an update still sees the cached
 *       pre-update value until the commit. No current code path does this.</li>
 *   <li>Size limit and TTL are safety nets against unbounded growth; correctness relies on eviction, not on TTL.</li>
 * </ul>
 * The cache lives in this JVM only. Running several instances would need a shared cache or cross-instance
 * invalidation.
 */
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(30)));
        caffeineCacheManager.setAllowNullValues(false);
        return new TransactionAwareCacheManagerProxy(caffeineCacheManager);
    }
}
