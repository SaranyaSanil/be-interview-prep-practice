package com.interviewprep.product;

import java.math.BigDecimal;

import com.interviewprep.product.dto.ProductPageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * All filters are optional and can be combined. Paging and sorting use Spring Data's standard parameters, e.g.
     * {@code ?page=0&size=20&sort=price,desc&sort=name}. The page size is capped at 100
     * ({@code spring.data.web.pageable.max-page-size}).
     */
    @GetMapping
    public ProductPageResponse list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) String name,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return productService.findAll(new ProductFilter(category, minPrice, maxPrice, inStock, name), pageable);
    }
}
