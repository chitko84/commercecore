package com.commercecore.category;

import com.commercecore.audit.AuditService;
import com.commercecore.common.exception.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class CategoryService {
    private final CategoryRepository categories;
    private final AuditService audits;
    public CategoryService(CategoryRepository categories, AuditService audits) {
        this.categories = categories; this.audits = audits;
    }

    @Transactional(readOnly = true)
    public List<CategoryDtos.CategoryResponse> publicRoots() {
        return categories.findAllByParentCategoryIsNullAndActiveTrueOrderByNameAsc().stream()
                .map(category -> response(category, true)).toList();
    }

    @Transactional(readOnly = true)
    public CategoryDtos.CategoryResponse publicDetail(String requestedSlug) {
        Category category = categories.findBySlugAndActiveTrue(Category.normalizeSlug(requestedSlug))
                .orElseThrow(CategoryService::notFound);
        return response(category, true);
    }

    @Transactional(readOnly = true)
    public List<CategoryDtos.CategorySummary> publicChildren(String requestedSlug) {
        Category category = categories.findBySlugAndActiveTrue(Category.normalizeSlug(requestedSlug))
                .orElseThrow(CategoryService::notFound);
        return categories.findAllByParentCategoryIdAndActiveTrueOrderByNameAsc(category.getId()).stream()
                .map(CategoryService::summary).toList();
    }

    @Transactional
    public CategoryDtos.CategoryResponse create(UUID actorId, CategoryDtos.CategoryRequest request) {
        String slug = normalizeSlug(request.slug());
        if (categories.existsBySlug(slug)) throw duplicateSlug();
        Category parent = parent(request.parentCategoryId());
        requireActiveParentForActiveCategory(parent, request.active());
        Category category = new Category(request.name(), slug, request.description(), parent, request.active());
        try {
            categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException conflict) {
            throw duplicateSlug();
        }
        audits.record(actorId, "CATEGORY_CREATED", "CATEGORY", "CATEGORY", category.getId().toString(),
                "slug=" + slug + "; name=" + category.getName());
        return response(category, false);
    }

    @Transactional
    public CategoryDtos.CategoryResponse update(UUID actorId, UUID id, CategoryDtos.CategoryRequest request) {
        Category category = required(id);
        String slug = normalizeSlug(request.slug());
        if (categories.existsBySlugAndIdNot(slug, id)) throw duplicateSlug();
        Category newParent = parent(request.parentCategoryId());
        validateParent(category, newParent);
        requireActiveParentForActiveCategory(newParent, request.active());
        category.update(request.name(), slug, request.description(), newParent, request.active());
        if (!request.active()) deactivateDescendants(category, new HashSet<>());
        try {
            categories.flush();
        } catch (DataIntegrityViolationException conflict) {
            throw duplicateSlug();
        }
        audits.record(actorId, "CATEGORY_UPDATED", "CATEGORY", "CATEGORY", category.getId().toString(),
                "slug=" + slug + "; name=" + category.getName() + "; active=" + category.isActive());
        return response(category, false);
    }

    @Transactional
    public CategoryDtos.CategoryResponse activate(UUID actorId, UUID id) {
        Category category = required(id);
        requireActiveParentForActiveCategory(category.getParentCategory(), true);
        category.activate();
        audits.record(actorId, "CATEGORY_ACTIVATED", "CATEGORY", "CATEGORY", category.getId().toString(),
                "slug=" + category.getSlug());
        return response(category, false);
    }

    @Transactional
    public CategoryDtos.CategoryResponse deactivate(UUID actorId, UUID id) {
        Category category = required(id);
        category.deactivate();
        deactivateDescendants(category, new HashSet<>());
        audits.record(actorId, "CATEGORY_DEACTIVATED", "CATEGORY", "CATEGORY", category.getId().toString(),
                "slug=" + category.getSlug());
        return response(category, false);
    }

    @Transactional(readOnly = true)
    public Category required(UUID id) {
        return categories.findById(id).orElseThrow(CategoryService::notFound);
    }

    private Category parent(UUID id) { return id == null ? null : required(id); }

    private void validateParent(Category category, Category candidate) {
        if (candidate == null) return;
        if (candidate.getId().equals(category.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_PARENT", "A category cannot be its own parent.");
        }
        Set<UUID> visited = new HashSet<>();
        Category cursor = candidate;
        while (cursor != null && visited.add(cursor.getId())) {
            if (cursor.getId().equals(category.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_CYCLE", "The category hierarchy cannot contain a cycle.");
            }
            cursor = cursor.getParentCategory();
        }
    }

    private void deactivateDescendants(Category category, Set<UUID> visited) {
        if (!visited.add(category.getId())) return;
        for (Category child : categories.findAllByParentCategoryIdOrderByNameAsc(category.getId())) {
            child.deactivate();
            deactivateDescendants(child, visited);
        }
    }

    private static void requireActiveParentForActiveCategory(Category parent, boolean active) {
        if (active && parent != null && !parent.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY_PARENT",
                    "An active category requires an active parent.");
        }
    }

    private CategoryDtos.CategoryResponse response(Category category, boolean publicOnly) {
        List<Category> children = publicOnly
                ? categories.findAllByParentCategoryIdAndActiveTrueOrderByNameAsc(category.getId())
                : categories.findAllByParentCategoryIdOrderByNameAsc(category.getId());
        return new CategoryDtos.CategoryResponse(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.getParentCategory() == null ? null : summary(category.getParentCategory()),
                children.stream().map(CategoryService::summary).toList(), category.isActive(),
                category.getCreatedAt(), category.getUpdatedAt());
    }

    public static CategoryDtos.CategorySummary summary(Category category) {
        return new CategoryDtos.CategorySummary(category.getId(), category.getName(), category.getSlug());
    }
    private static String normalizeSlug(String slug) {
        try { return Category.normalizeSlug(slug); }
        catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLUG", "The category slug is invalid.");
        }
    }
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "The category was not found.");
    }
    private static ApiException duplicateSlug() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SLUG", "The category slug is already in use.");
    }
}
