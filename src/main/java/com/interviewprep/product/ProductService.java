package com.interviewprep.product;

import java.math.BigDecimal;
import java.util.List;

import com.interviewprep.common.exception.FieldValidationException;
import com.interviewprep.product.dto.ProductPageResponse;
import com.interviewprep.product.dto.ProductResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

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
        Pageable safePageable = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), withIdTieBreaker(validatedSort(pageable.getSort())));
        return ProductPageResponse.from(
                productRepository.findAll(ProductSpecifications.matching(filter), safePageable)
                        .map(ProductResponse::from));
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
