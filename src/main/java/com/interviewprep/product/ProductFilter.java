package com.interviewprep.product;

import java.math.BigDecimal;

/**
 * Optional list filters; any combination may be set. A null field means "do not filter on this".
 *
 * @param inStock {@code true} keeps only products with stock above zero; null or false applies no stock filter
 */
public record ProductFilter(String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, String name) {
}
