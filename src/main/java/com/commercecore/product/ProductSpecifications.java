package com.commercecore.product;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.util.*;

final class ProductSpecifications {
    private ProductSpecifications() { }

    static Specification<Product> catalog(ProductService.SearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (criteria.active() != null) predicates.add(cb.equal(root.get("active"), criteria.active()));
            if (Boolean.TRUE.equals(criteria.requireActiveCategory())) {
                predicates.add(cb.isTrue(root.get("category").get("active")));
            }
            if (criteria.categoryIds() != null) {
                predicates.add(root.get("category").get("id").in(criteria.categoryIds()));
            }
            if (criteria.brand() != null) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), criteria.brand().toLowerCase(Locale.ROOT)));
            }
            Expression<BigDecimal> effectivePrice = cb.<BigDecimal>coalesce()
                    .value(root.get("salePrice")).value(root.get("basePrice"));
            if (criteria.minimumPrice() != null) predicates.add(cb.greaterThanOrEqualTo(effectivePrice, criteria.minimumPrice()));
            if (criteria.maximumPrice() != null) predicates.add(cb.lessThanOrEqualTo(effectivePrice, criteria.maximumPrice()));
            if (criteria.query() != null) {
                String pattern = "%" + escapeLike(criteria.query().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sku")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(root.get("brand")), pattern, '\\')));
            }
            if (!Long.class.equals(query.getResultType()) && !long.class.equals(query.getResultType())) {
                var id = root.get("id");
                switch (criteria.sort()) {
                    case PRICE_ASC -> query.orderBy(cb.asc(effectivePrice), cb.asc(id));
                    case PRICE_DESC -> query.orderBy(cb.desc(effectivePrice), cb.asc(id));
                    case NAME -> query.orderBy(cb.asc(cb.lower(root.get("name"))), cb.asc(id));
                    case OLDEST -> query.orderBy(cb.asc(root.get("createdAt")), cb.asc(id));
                    case NEWEST -> query.orderBy(cb.desc(root.get("createdAt")), cb.asc(id));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
