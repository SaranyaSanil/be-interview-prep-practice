package com.interviewprep.product;

import java.math.BigDecimal;
import java.util.List;

import com.interviewprep.common.exception.FieldValidationException;
import com.interviewprep.common.exception.ResourceNotFoundException;
import com.interviewprep.product.dto.ProductPageResponse;
import com.interviewprep.product.dto.ProductResponse;
import com.interviewprep.product.dto.UpdateProductRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    /** Single-product cache, keyed by product id; values are immutable {@link ProductResponse} records. */
    public static final String PRODUCTS_CACHE = "products";

    /** Fields clients may sort by. Anything else is rejected rather than passed to the query. */
    static final List<String> SORTABLE_FIELDS =
            List.of("id", "name", "category", "price", "stock", "rating", "createdAt");
    private static final int NAME_FILTER_MAX_LENGTH = 100;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductPageResponse findAll(ProductFilter filter, Pageable pageable) {
        validate(filter);
        if ((long) pageable.getPageNumber() * pageable.getPageSize() > Integer.MAX_VALUE) {
            throw new FieldValidationException("page", "is too large");
        }
        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), withIdTieBreaker(validatedSort(pageable.getSort())));
        return ProductPageResponse.from(
                productRepository.findAll(ProductSpecifications.matching(filter), safePageable)
                        .map(ProductResponse::from));
    }

    /**
     * Cached by id. On a hit the caching proxy returns the stored response without calling this method, so the
     * database is not queried. {@code sync = true} makes concurrent misses for the same id load it only once.
     * Unknown ids throw, and exceptions are never cached.
     */
    @Cacheable(cacheNames = PRODUCTS_CACHE, key = "#id", sync = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getProduct(id));
    }

    /** Evicts (not re-populates) the entry after the commit, so the next read loads the committed row. */
    @Transactional
    @CacheEvict(cacheNames = PRODUCTS_CACHE, key = "#id")
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = getProduct(id);
        product.update(request.name(), request.category(), request.price(), request.stock(), request.rating());
        return ProductResponse.from(product);
    }

    @Transactional
    @CacheEvict(cacheNames = PRODUCTS_CACHE, key = "#id")
    public void delete(Long id) {
        productRepository.delete(getProduct(id));
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + id + " not found"));
    }

    private static void validate(ProductFilter filter) {
        requireNonNegative("minPrice", filter.minPrice());
        requireNonNegative("maxPrice", filter.maxPrice());
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new FieldValidationException("minPrice", "must not be greater than maxPrice");
        }
        if (filter.name() != null && filter.name().length() > NAME_FILTER_MAX_LENGTH) {
            throw new FieldValidationException("name", "size must be between 0 and " + NAME_FILTER_MAX_LENGTH);
        }
    }

    private static void requireNonNegative(String field, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new FieldValidationException(field, "must be greater than or equal to 0");
        }
    }

    private static Sort validatedSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new FieldValidationException("sort", "must be one of " + SORTABLE_FIELDS);
            }
        }
        return sort;
    }

    /** Rows with equal sort values (e.g. same price) need a unique final key, or pages can repeat or skip rows. */
    private static Sort withIdTieBreaker(Sort sort) {
        return sort.getOrderFor("id") == null ? sort.and(Sort.by("id")) : sort;
    }
}
