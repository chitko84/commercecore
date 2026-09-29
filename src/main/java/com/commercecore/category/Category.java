package com.commercecore.category;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Entity
@Table(name = "categories")
public class Category {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, unique = true, length = 140) private String slug;
    @Column(length = 1000) private String description;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id") private Category parentCategory;
    @Column(nullable = false) private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Category() { }

    public Category(String name, String slug, String description, Category parentCategory, boolean active) {
        update(name, slug, description, parentCategory, active);
    }

    public void update(String name, String slug, String description, Category parentCategory, boolean active) {
        this.name = required(name, "name", 120);
        this.slug = normalizeSlug(slug);
        this.description = optional(description, 1000);
        this.parentCategory = parentCategory;
        this.active = active;
    }

    public void activate() { active = true; }
    public void deactivate() { active = false; }

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public static String normalizeSlug(String value) {
        String normalized = required(value, "slug", 140).toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(normalized).matches()) {
            throw new IllegalArgumentException("slug must use lowercase letters, numbers and single hyphens");
        }
        return normalized;
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException(field + " is too long");
        return trimmed;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > max) throw new IllegalArgumentException("description is too long");
        return trimmed;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public Category getParentCategory() { return parentCategory; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
