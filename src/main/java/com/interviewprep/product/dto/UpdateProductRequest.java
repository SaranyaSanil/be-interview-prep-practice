package com.interviewprep.product.dto;

import java.math.BigDecimal;

import com.interviewprep.product.Product;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement (PUT) of a product's editable fields. {@code id} and {@code createdAt} are server-managed. */
public record UpdateProductRequest(
        @NotBlank @Size(max = Product.NAME_MAX_LENGTH) String name,
        @NotBlank @Size(max = Product.CATEGORY_MAX_LENGTH) String category,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) Integer stock,
        @NotNull @DecimalMin("0.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 1) BigDecimal rating) {
}
