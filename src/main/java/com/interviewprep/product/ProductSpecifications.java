package com.interviewprep.product;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/**
 * One small specification per filter. Each returns {@code null} when its filter is absent, and
 * {@link Specification#allOf} ignores nulls, so any combination of filters becomes a single WHERE clause.
 */
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(ProductFilter filter) {
        return Specification.allOf(
                hasCategory(filter.category()),
                priceAtLeast(filter.minPrice()),
                priceAtMost(filter.maxPrice()),
                inStockOnly(filter.inStock()),
                nameContains(filter.name()));
    }

    static Specification<Product> hasCategory(String category) {
        if (isBlank(category)) {
            return null;
        }
        String value = category.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("category")), value);
    }

    static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        return minPrice == null ? null : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        return maxPrice == null ? null : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    static Specification<Product> inStockOnly(Boolean inStock) {
        return Boolean.TRUE.equals(inStock) ? (root, query, cb) -> cb.greaterThan(root.get("stock"), 0) : null;
    }

    /** Case-insensitive "contains". {@code %} and {@code _} in the search text are matched literally. */
    static Specification<Product> nameContains(String name) {
        if (isBlank(name)) {
            return null;
        }
        String pattern = "%" + escapeLike(name.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
