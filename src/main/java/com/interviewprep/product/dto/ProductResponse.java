package com.interviewprep.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.interviewprep.product.Product;

/** Immutable, so it is also safe to share from the cache across requests. */
public record ProductResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        int stock,
        BigDecimal rating,
        Instant createdAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStock(),
                product.getRating(),
                product.getCreatedAt());
    }
}
