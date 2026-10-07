package com.interviewprep.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Runs against the 100 products seeded at startup. Expected counts are derived from the seed formula
 * ({@link ProductSeeder#product}) rather than hard-coded, so the tests explain where each number comes from.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductQueryIntegrationTest {

    private static final List<Product> SEED = IntStream.rangeClosed(1, ProductSeeder.PRODUCT_COUNT)
            .mapToObj(i -> ProductSeeder.product(i, Instant.EPOCH))
            .toList();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductSeeder productSeeder;

    @Test
    void seedsOneHundredProductsOnlyOnce() {
        assertThat(productRepository.count()).isEqualTo(100);

        productSeeder.run(null);

        assertThat(productRepository.count()).isEqualTo(100);
    }

    @Test
    void defaultPageReturnsTwentyProductsWithTotals() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(20)))
                .andExpect(jsonPath("$.totalCount").value(100))
                .andExpect(jsonPath("$.totalPages").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void pagesDoNotOverlap() throws Exception {
        List<Integer> first = ids(get("/api/products").param("size", "10").param("page", "0").param("sort", "stock"));
        List<Integer> second = ids(get("/api/products").param("size", "10").param("page", "1").param("sort", "stock"));

        assertThat(first).hasSize(10).doesNotContainAnyElementsOf(second);
    }

    @Test
    void pageSizeIsCappedAtOneHundred() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.products", hasSize(100)))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void filtersByCategoryCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/products").param("category", "books").param("size", "100"))
                .andExpect(jsonPath("$.totalCount").value(expectedCount(p -> p.getCategory().equals("Books"))))
                .andExpect(jsonPath("$.products[*].category", everyItem(is("Books"))));
    }

    @Test
    void filtersInStockOnly() throws Exception {
        mockMvc.perform(get("/api/products").param("inStock", "true"))
                .andExpect(jsonPath("$.totalCount").value(expectedCount(p -> p.getStock() > 0)));
        mockMvc.perform(get("/api/products").param("inStock", "false"))
                .andExpect(jsonPath("$.totalCount").value(100));
    }

    @Test
    void filtersByInclusivePriceRange() throws Exception {
        BigDecimal min = new BigDecimal("10.99");
        BigDecimal max = new BigDecimal("20.99");
        mockMvc.perform(get("/api/products").param("minPrice", "10.99").param("maxPrice", "20.99"))
                .andExpect(jsonPath("$.totalCount").value(expectedCount(
                        p -> p.getPrice().compareTo(min) >= 0 && p.getPrice().compareTo(max) <= 0)));
    }

    @Test
    void searchesNameCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/products").param("name", "LAMP"))
                .andExpect(jsonPath("$.totalCount").value(expectedCount(p -> p.getName().startsWith("Lamp"))));
    }

    @Test
    void likeWildcardsInSearchAreMatchedLiterally() throws Exception {
        mockMvc.perform(get("/api/products").param("name", "%"))
                .andExpect(jsonPath("$.totalCount").value(0));
        mockMvc.perform(get("/api/products").param("name", "_"))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void combinesAllFiltersInOneRequest() throws Exception {
        long expected = expectedCount(p -> p.getCategory().equals("Home")
                && p.getStock() > 0
                && p.getPrice().compareTo(new BigDecimal("50")) >= 0
                && p.getPrice().compareTo(new BigDecimal("200")) <= 0
                && p.getName().toLowerCase().contains("lamp"));
        assertThat(expected).isPositive();

        mockMvc.perform(get("/api/products")
                        .param("category", "Home")
                        .param("inStock", "true")
                        .param("minPrice", "50")
                        .param("maxPrice", "200")
                        .param("name", "lamp")
                        .param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(expected))
                .andExpect(jsonPath("$.products[*].category", everyItem(is("Home"))));
    }

    @Test
    void sortsByPriceDescending() throws Exception {
        String body = mockMvc.perform(get("/api/products").param("sort", "price,desc").param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        List<Double> prices = JsonPath.read(body, "$.products[*].price");

        assertThat(prices).isSortedAccordingTo(Comparator.reverseOrder());
        assertThat(prices.get(0)).isEqualTo(250.99);
    }

    @Test
    void equalSortValuesAreOrderedByIdForStablePages() throws Exception {
        String body = mockMvc.perform(get("/api/products").param("sort", "category").param("size", "100"))
                .andReturn().getResponse().getContentAsString();
        List<String> categories = JsonPath.read(body, "$.products[*].category");
        List<Integer> ids = JsonPath.read(body, "$.products[*].id");

        for (int i = 1; i < ids.size(); i++) {
            if (categories.get(i).equals(categories.get(i - 1))) {
                assertThat(ids.get(i)).isGreaterThan(ids.get(i - 1));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "name", "category", "price", "stock", "rating", "createdAt"})
    void canSortByEveryProductField(String field) throws Exception {
        mockMvc.perform(get("/api/products").param("sort", field + ",desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(20)));
    }

    @Test
    void rejectsSortingByUnknownField() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must be one of [id, name, category, price, stock, rating, createdAt]"));
    }

    @Test
    void rejectsInvalidPriceRange() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("minPrice"));
        mockMvc.perform(get("/api/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("minPrice"));
        mockMvc.perform(get("/api/products").param("maxPrice", "cheap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("must be a number"));
    }

    @Test
    void rejectsPageNumberThatWouldOverflowTheOffset() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "30000000").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }

    @Test
    void writesStillRejectAnInvalidTokenWhileReadsIgnoreIt() throws Exception {
        mockMvc.perform(delete("/api/products/1").header("Authorization", "Bearer expired-or-garbage"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer expired-or-garbage"))
                .andExpect(status().isOk());
        assertThat(productRepository.count()).isEqualTo(100);
    }

    private static long expectedCount(Predicate<Product> predicate) {
        return SEED.stream().filter(predicate).count();
    }

    private List<Integer> ids(MockHttpServletRequestBuilder request) throws Exception {
        return JsonPath.read(mockMvc.perform(request).andReturn().getResponse().getContentAsString(),
                "$.products[*].id");
    }
}
