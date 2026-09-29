package com.commercecore.category;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
public class PublicCategoryController {
    private final CategoryService categories;
    public PublicCategoryController(CategoryService categories) { this.categories = categories; }

    @GetMapping
    List<CategoryDtos.CategoryResponse> roots() { return categories.publicRoots(); }

    @GetMapping("/{slug}")
    CategoryDtos.CategoryResponse detail(@PathVariable String slug) { return categories.publicDetail(slug); }

    @GetMapping("/{slug}/children")
    List<CategoryDtos.CategorySummary> children(@PathVariable String slug) {
        return categories.publicChildren(slug);
    }
}
