package com.commercecore.product;

import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

@Validated
@RestController
@RequestMapping("/api/v1/products")
public class PublicProductController {
    private final ProductService products;
    public PublicProductController(ProductService products) { this.products = products; }

    @GetMapping
    ProductDtos.PageResponse<ProductDtos.ProductSummary> search(
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 140) String category,
            @RequestParam(required = false) @Size(max = 120) String brand,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "newest") String sort) {
        return products.publicSearch(q, category, brand, minPrice, maxPrice, page, size, sort);
    }

    @GetMapping("/{slug}")
    ProductDtos.ProductResponse detail(@PathVariable String slug) { return products.publicDetail(slug); }
}
