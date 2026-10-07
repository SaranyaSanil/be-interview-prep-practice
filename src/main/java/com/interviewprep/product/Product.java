package com.interviewprep.product;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_category", columnList = "category"),
        @Index(name = "idx_products_price", columnList = "price")})
public class Product {

    public static final int NAME_MAX_LENGTH = 100;
    public static final int CATEGORY_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(nullable = false, length = CATEGORY_MAX_LENGTH)
    private String category;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int stock;

    /** 0.0 to 5.0 with one decimal. */
    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Product() {
        // required by JPA
    }

    public Product(String name, String category, BigDecimal price, int stock, BigDecimal rating, Instant createdAt) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.rating = rating;
        this.createdAt = createdAt;
    }

    public void update(String name, String category, BigDecimal price, int stock, BigDecimal rating) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
        this.rating = rating;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
