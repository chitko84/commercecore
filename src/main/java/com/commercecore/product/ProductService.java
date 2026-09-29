package com.commercecore.product;

import com.commercecore.audit.AuditService;
import com.commercecore.cache.CatalogCache;
import com.commercecore.category.*;
import com.commercecore.common.exception.ApiException;
import com.commercecore.inventory.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ProductService {
    private final ProductRepository products;
    private final ProductMediaRepository media;
    private final CategoryRepository categories;
    private final CategoryService categoryService;
    private final ProductVariantService variants;
    private final InventoryService inventory;
    private final AuditService audits;
    private final CatalogCache cache;

    public ProductService(ProductRepository products, ProductMediaRepository media,
                          CategoryRepository categories, CategoryService categoryService,
                          ProductVariantService variants, InventoryService inventory,
                          AuditService audits, CatalogCache cache) {
        this.products = products; this.media = media; this.categories = categories; this.categoryService = categoryService;
        this.variants = variants; this.inventory = inventory; this.audits = audits;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public ProductDtos.PageResponse<ProductDtos.ProductSummary> publicSearch(
            String query, String categorySlug, String brand, BigDecimal minimumPrice,
            BigDecimal maximumPrice, int page, int size, String sort) {
        SearchCriteria criteria = criteria(query, categorySlug, brand, minimumPrice, maximumPrice,
                true, true, sort);
        return page(products.findAll(ProductSpecifications.catalog(criteria), PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public ProductDtos.PageResponse<ProductDtos.ProductSummary> staffSearch(
            String query, String categorySlug, String brand, BigDecimal minimumPrice,
            BigDecimal maximumPrice, Boolean active, int page, int size, String sort) {
        SearchCriteria criteria = criteria(query, categorySlug, brand, minimumPrice, maximumPrice,
                active, false, sort);
        return page(products.findAll(ProductSpecifications.catalog(criteria), PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductResponse publicDetail(String requestedSlug) {
        String slug = normalizeSlug(requestedSlug);
        return cache.get(CatalogCache.product(slug),ProductDtos.ProductResponse.class,()->loadPublicDetail(slug));
    }

    private ProductDtos.ProductResponse loadPublicDetail(String slug) {
        Product product = products.findBySlugAndActiveTrueAndCategoryActiveTrue(slug)
                .orElseThrow(ProductService::notFound);
        return response(product, true);
    }

    @Transactional(readOnly = true)
    public ProductDtos.ProductResponse staffDetail(UUID id) { return response(required(id), false); }

    @Transactional
    public ProductDtos.ProductResponse create(UUID actorId, ProductDtos.ProductRequest request) {
        String slug = normalizeSlug(request.slug());
        String sku = normalizeSku(request.sku());
        requireUnique(slug, sku, null);
        validatePrices(request.basePrice(), request.salePrice());
        Category category = categoryService.required(request.categoryId());
        requireActiveCategory(category, request.active());
        Product product = new Product(request.name(), slug, request.description(), sku, request.brand(),
                request.basePrice(), request.salePrice(), category, request.active());
        try {
            products.saveAndFlush(product);
        } catch (DataIntegrityViolationException conflict) {
            throw catalogConflict();
        }
        audits.record(actorId, "PRODUCT_CREATED", "CATALOG", "PRODUCT", product.getId().toString(),
                "slug=" + slug + "; sku=" + sku + "; name=" + product.getName()
                        + "; basePrice=" + product.getBasePrice());
        return response(product, false);
    }

    @Transactional
    public ProductDtos.ProductResponse update(UUID actorId, UUID id, ProductDtos.ProductRequest request) {
        Product product = required(id);
        String previousSlug=product.getSlug();
        boolean priceChanged = product.getBasePrice().compareTo(request.basePrice()) != 0
                || (product.getSalePrice() == null && request.salePrice() != null)
                || (product.getSalePrice() != null && !product.getSalePrice().equals(request.salePrice()));
        String slug = normalizeSlug(request.slug());
        String sku = normalizeSku(request.sku());
        requireUnique(slug, sku, id);
        validatePrices(request.basePrice(), request.salePrice());
        Category category = categoryService.required(request.categoryId());
        requireActiveCategory(category, request.active());
        product.update(request.name(), slug, request.description(), sku, request.brand(), request.basePrice(),
                request.salePrice(), category, request.active());
        try {
            products.flush();
        } catch (DataIntegrityViolationException conflict) {
            throw catalogConflict();
        }
        audits.record(actorId, "PRODUCT_UPDATED", "CATALOG", "PRODUCT", product.getId().toString(),
                "slug=" + slug + "; name=" + product.getName() + "; active=" + product.isActive());
        if (priceChanged) {
            audits.record(actorId, "PRODUCT_PRICE_UPDATED", "CATALOG", "PRODUCT", product.getId().toString(),
                    "basePrice=" + request.basePrice()
                            + (request.salePrice() == null ? "" : "; salePrice=" + request.salePrice()));
        }
        cache.evictAfterCommit(CatalogCache.product(previousSlug),CatalogCache.product(slug));
        return response(product, false);
    }

    @Transactional
    public ProductDtos.ProductResponse activate(UUID actorId, UUID id) {
        Product product = required(id);
        requireActiveCategory(product.getCategory(), true);
        product.activate();
        cache.evictAfterCommit(CatalogCache.product(product.getSlug()));
        audits.record(actorId, "PRODUCT_ACTIVATED", "CATALOG", "PRODUCT", product.getId().toString(),
                "slug=" + product.getSlug());
        return response(product, false);
    }

    @Transactional
    public ProductDtos.ProductResponse deactivate(UUID actorId, UUID id) {
        Product product = required(id);
        product.deactivate();
        cache.evictAfterCommit(CatalogCache.product(product.getSlug()));
        audits.record(actorId, "PRODUCT_DEACTIVATED", "CATALOG", "PRODUCT", product.getId().toString(),
                "slug=" + product.getSlug());
        return response(product, false);
    }

    @Transactional(readOnly = true)
    public Product required(UUID id) {
        return products.findById(id).orElseThrow(ProductService::notFound);
    }

    ProductDtos.ProductResponse response(Product product, boolean publicOnly) {
        List<ProductDtos.MediaResponse> entries = media.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(product.getId())
                .stream().map(ProductService::mediaResponse).toList();
        List<VariantDtos.VariantResponse> productVariants = publicOnly
                ? variants.publicList(product) : variants.staffList(product.getId());
        return new ProductDtos.ProductResponse(product.getId(), product.getName(), product.getSlug(),
                product.getDescription(), product.getSku(), product.getBrand(), product.getBasePrice(),
                product.getSalePrice(), product.effectivePrice(), CategoryService.summary(product.getCategory()),
                entries, productVariants, product.isActive(), availability(product, productVariants),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    private ProductDtos.PageResponse<ProductDtos.ProductSummary> page(Page<Product> result) {
        List<ProductDtos.ProductSummary> content = result.getContent().stream().map(this::summary).toList();
        return new ProductDtos.PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), result.isFirst(), result.isLast());
    }

    private ProductDtos.ProductSummary summary(Product product) {
        ProductDtos.MediaResponse primary = media.findAllByProductIdOrderByDisplayOrderAscCreatedAtAsc(product.getId())
                .stream().filter(ProductMedia::isPrimary).findFirst().map(ProductService::mediaResponse).orElse(null);
        return new ProductDtos.ProductSummary(product.getId(), product.getName(), product.getSlug(), product.getSku(),
                product.getBrand(), product.getBasePrice(), product.getSalePrice(), product.effectivePrice(),
                CategoryService.summary(product.getCategory()), primary, product.isActive(),
                availability(product, variants.publicList(product)), product.getCreatedAt());
    }

    private StockStatus availability(Product product, List<VariantDtos.VariantResponse> productVariants) {
        if (productVariants.isEmpty()) return inventory.productStatus(product.getId());
        List<StockStatus> states = productVariants.stream().map(VariantDtos.VariantResponse::availability).toList();
        if (states.contains(StockStatus.IN_STOCK)) return StockStatus.IN_STOCK;
        if (states.contains(StockStatus.LOW_STOCK)) return StockStatus.LOW_STOCK;
        if (states.stream().allMatch(s -> s == StockStatus.OUT_OF_STOCK)) return StockStatus.OUT_OF_STOCK;
        return StockStatus.UNKNOWN;
    }

    static ProductDtos.MediaResponse mediaResponse(ProductMedia value) {
        return new ProductDtos.MediaResponse(value.getId(), value.getMediaUrl(), value.getAltText(),
                value.getDisplayOrder(), value.isPrimary(), value.getCreatedAt());
    }

    private SearchCriteria criteria(String query, String categorySlug, String brand, BigDecimal min,
                                    BigDecimal max, Boolean active, boolean publicOnly, String sort) {
        if (min != null && min.signum() < 0 || max != null && max.signum() < 0 ||
                min != null && max != null && min.compareTo(max) > 0) throw invalidPrice();
        Set<UUID> categoryIds = categorySlug == null || categorySlug.isBlank()
                ? null : categorySubtree(categorySlug, publicOnly);
        return new SearchCriteria(text(query), categoryIds, text(brand), min, max, active,
                publicOnly, SortMode.parse(sort));
    }

    private Set<UUID> categorySubtree(String slug, boolean publicOnly) {
        String normalized;
        try { normalized = Category.normalizeSlug(slug); }
        catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLUG", "The category slug is invalid.");
        }
        Category root = (publicOnly
                ? categories.findBySlugAndActiveTrue(normalized)
                : categories.findBySlug(normalized)).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "The category was not found."));
        Set<UUID> ids = new LinkedHashSet<>();
        collectCategoryIds(root, publicOnly, ids);
        return ids;
    }

    private void collectCategoryIds(Category category, boolean publicOnly, Set<UUID> ids) {
        ids.add(category.getId());
        List<Category> children = publicOnly
                ? categories.findAllByParentCategoryIdAndActiveTrueOrderByNameAsc(category.getId())
                : categories.findAllByParentCategoryIdOrderByNameAsc(category.getId());
        for (Category child : children) collectCategoryIds(child, publicOnly, ids);
    }

    private void requireUnique(String slug, String sku, UUID currentId) {
        boolean slugExists = currentId == null ? products.existsBySlug(slug) : products.existsBySlugAndIdNot(slug, currentId);
        if (slugExists) throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SLUG", "The product slug is already in use.");
        boolean skuExists = currentId == null ? products.existsBySku(sku) : products.existsBySkuAndIdNot(sku, currentId);
        if (skuExists || variants.skuExists(sku)) throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SKU", "The SKU is already in use.");
    }

    private static void requireActiveCategory(Category category, boolean productActive) {
        if (productActive && !category.isActive()) throw new ApiException(HttpStatus.BAD_REQUEST,
                "CATEGORY_INACTIVE", "An active product requires an active category.");
    }
    private static void validatePrices(BigDecimal base, BigDecimal sale) {
        if (base == null || base.signum() <= 0 || sale != null && (sale.signum() <= 0 || sale.compareTo(base) > 0))
            throw invalidPrice();
    }
    private static String normalizeSlug(String value) {
        try { return Product.normalizeSlug(value); }
        catch (IllegalArgumentException invalid) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SLUG", "The product slug is invalid."); }
    }
    private static String normalizeSku(String value) {
        try { return Product.normalizeSku(value); }
        catch (IllegalArgumentException invalid) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SKU", "The SKU is invalid."); }
    }
    private static String text(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ApiException invalidPrice() { return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE", "The product price is invalid."); }
    private static ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "The product was not found."); }
    private static ApiException catalogConflict() { return new ApiException(HttpStatus.CONFLICT, "CATALOG_CONFLICT", "The catalog record conflicts with existing data."); }

    record SearchCriteria(String query, Set<UUID> categoryIds, String brand, BigDecimal minimumPrice,
                          BigDecimal maximumPrice, Boolean active, Boolean requireActiveCategory, SortMode sort) { }
    enum SortMode {
        NEWEST, PRICE_ASC, PRICE_DESC, NAME, OLDEST;
        static SortMode parse(String value) {
            if (value == null || value.isBlank()) return NEWEST;
            try { return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_')); }
            catch (IllegalArgumentException invalid) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SORT", "Unsupported product sort option.");
            }
        }
    }
}
