package com.interviewprep.product.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Explicit page shape. Spring's {@code Page} is not serialized directly because its JSON layout is not a stable API.
 *
 * @param page zero-based page number
 * @param size the page size actually applied (requests above the maximum of 100 are capped)
 */
public record ProductPageResponse(
        List<ProductResponse> products,
        long totalCount,
        int totalPages,
        int page,
        int size) {

    public static ProductPageResponse from(Page<ProductResponse> page) {
        return new ProductPageResponse(
                page.getContent(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
}
