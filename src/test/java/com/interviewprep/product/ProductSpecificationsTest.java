package com.interviewprep.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductSpecificationsTest {

    @Test
    void absentFiltersProduceNoCondition() {
        assertThat(ProductSpecifications.hasCategory(null)).isNull();
        assertThat(ProductSpecifications.hasCategory("  ")).isNull();
        assertThat(ProductSpecifications.priceAtLeast(null)).isNull();
        assertThat(ProductSpecifications.priceAtMost(null)).isNull();
        assertThat(ProductSpecifications.inStockOnly(null)).isNull();
        assertThat(ProductSpecifications.inStockOnly(false)).isNull();
        assertThat(ProductSpecifications.nameContains("")).isNull();
    }

    @Test
    void escapesLikeWildcards() {
        assertThat(ProductSpecifications.escapeLike("50%_off\\")).isEqualTo("50\\%\\_off\\\\");
    }
}
