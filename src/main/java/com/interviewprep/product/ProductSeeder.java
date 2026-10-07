package com.interviewprep.product;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds 100 products at startup, only when the table is empty, so restarting against a persistent database never
 * duplicates data. Values come from simple formulas rather than randomness, so demos and tests can rely on them:
 * <ul>
 *   <li>categories cycle through 5 values (20 products each), and each category has its own product noun</li>
 *   <li>every 7th product is out of stock (14 in total)</li>
 *   <li>prices run from 3.49 to 250.99, ratings from 1.0 to 5.0, and creation dates go back one day per product</li>
 * </ul>
 */
@Component
public class ProductSeeder implements ApplicationRunner {

    static final int PRODUCT_COUNT = 100;
    static final List<String> CATEGORIES = List.of("Electronics", "Books", "Home", "Sports", "Toys");
    private static final List<String> NOUNS = List.of("Headphones", "Novel", "Lamp", "Ball", "Puzzle");

    private static final Logger log = LoggerFactory.getLogger(ProductSeeder.class);

    private final ProductRepository productRepository;
    private final Clock clock;

    public ProductSeeder(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        productRepository.saveAll(IntStream.rangeClosed(1, PRODUCT_COUNT).mapToObj(i -> product(i, now)).toList());
        log.info("Seeded {} products", PRODUCT_COUNT);
    }

    static Product product(int i, Instant now) {
        int categoryIndex = (i - 1) % CATEGORIES.size();
        return new Product(
                "%s %03d".formatted(NOUNS.get(categoryIndex), i),
                CATEGORIES.get(categoryIndex),
                BigDecimal.valueOf(i * 250L + 99, 2),
                i % 7 == 0 ? 0 : (i * 13) % 50 + 1,
                BigDecimal.valueOf(10 + (i * 3) % 41, 1),
                now.minus(Duration.ofDays(i)));
    }
}
