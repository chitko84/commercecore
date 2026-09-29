package com.commercecore.product;

import com.commercecore.user.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class Phase3CatalogIT {
    private static final String PASSWORD = "Catalog-Test-42!";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired TestRestTemplate rest;
    @Autowired UserAccountRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayV1ThroughV5AndJpaValidationSupportCatalogPersistence() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");

        String staff = roleAccess("catalog.persistence.staff@example.com", UserRole.STAFF);
        JsonNode root = createCategory(staff, "Persistence Root", "persistence-root", null, true);
        JsonNode child = createCategory(staff, "Persistence Child", "persistence-child", uuid(root), true);
        JsonNode product = createProduct(staff, "Persistence Product", "persistence-product", "persist-001",
                "Persist Brand", "100.00", "80.00", uuid(child), true);

        assertThat(UUID.fromString(product.path("id").asText())).isNotNull();
        assertThat(product.path("sku").asText()).isEqualTo("PERSIST-001");
        assertThat(product.path("effectivePrice").decimalValue()).isEqualByComparingTo("80.00");
        UUID storedCategory = jdbc.queryForObject("select category_id from products where id = ?", UUID.class, uuid(product));
        assertThat(storedCategory).isEqualTo(uuid(child));
        UUID storedParent = jdbc.queryForObject("select parent_category_id from categories where id = ?", UUID.class, uuid(child));
        assertThat(storedParent).isEqualTo(uuid(root));
    }

    @Test
    void categoryHierarchyRejectsDuplicatesInvalidParentsAndCyclesAndDeactivatesSubtree() {
        String customer = roleAccess("category.customer@example.com", UserRole.CUSTOMER);
        String staff = roleAccess("category.staff@example.com", UserRole.STAFF);
        String admin = roleAccess("category.admin@example.com", UserRole.ADMIN);

        assertThat(postAuth("/api/v1/staff/categories", customer,
                category("Blocked", "blocked-category", null, true)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode root = createCategory(staff, "Electronics", "electronics-phase3", null, true);
        JsonNode child = createCategory(staff, "Laptops", "laptops-phase3", uuid(root), true);
        assertThat(createCategory(admin, "Admin Category", "admin-category-phase3", null, true)).isNotNull();

        ResponseEntity<JsonNode> duplicate = postAuth("/api/v1/staff/categories", staff,
                category("Duplicate", "ELECTRONICS-PHASE3", null, true));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().path("code").asText()).isEqualTo("DUPLICATE_SLUG");

        ResponseEntity<JsonNode> self = putAuth("/api/v1/staff/categories/" + uuid(child), staff,
                category("Laptops", "laptops-phase3", uuid(child), true));
        assertThat(self.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(self.getBody().path("code").asText()).isEqualTo("INVALID_CATEGORY_PARENT");

        ResponseEntity<JsonNode> cycle = putAuth("/api/v1/staff/categories/" + uuid(root), staff,
                category("Electronics", "electronics-phase3", uuid(child), true));
        assertThat(cycle.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(cycle.getBody().path("code").asText()).isEqualTo("CATEGORY_CYCLE");

        ResponseEntity<JsonNode> missingParent = postAuth("/api/v1/staff/categories", staff,
                category("Missing parent", "missing-parent-phase3", UUID.randomUUID(), true));
        assertThat(missingParent.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missingParent.getBody().path("code").asText()).isEqualTo("CATEGORY_NOT_FOUND");

        assertThat(rest.getForEntity("/api/v1/categories/electronics-phase3", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForObject("/api/v1/categories", JsonNode.class).toString()).contains("electronics-phase3");
        assertThat(rest.getForEntity("/api/v1/categories/electronics-phase3/children", JsonNode.class)
                .getBody()).hasSize(1);
        assertThat(postAuth("/api/v1/staff/categories/" + uuid(root) + "/deactivate", staff, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/v1/categories/electronics-phase3", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/v1/categories/laptops-phase3", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("select active from categories where id = ?", Boolean.class, uuid(child))).isFalse();
    }

    @Test
    void productManagementNormalizesUniquenessValidatesPricesUpdatesAndDeactivates() {
        String staff = roleAccess("product.staff@example.com", UserRole.STAFF);
        String customer = roleAccess("product.customer@example.com", UserRole.CUSTOMER);
        JsonNode category = createCategory(staff, "Product Category", "product-category-phase3", null, true);

        assertThat(postAuth("/api/v1/staff/products", customer,
                product("Blocked", "blocked-product", "BLOCK-1", "Brand", "10.00", null, uuid(category), true))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode created = createProduct(staff, "Catalog Laptop", "catalog-laptop-phase3", "lap-001",
                "CoreBrand", "1200.00", "999.99", uuid(category), true);
        assertThat(created.path("sku").asText()).isEqualTo("LAP-001");
        assertThat(rest.getForEntity("/api/v1/products/catalog-laptop-phase3", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> duplicateSku = postAuth("/api/v1/staff/products", staff,
                product("Duplicate SKU", "duplicate-sku-phase3", "LAP-001", "Other", "20.00", null, uuid(category), true));
        assertThat(duplicateSku.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateSku.getBody().path("code").asText()).isEqualTo("DUPLICATE_SKU");
        ResponseEntity<JsonNode> duplicateSlug = postAuth("/api/v1/staff/products", staff,
                product("Duplicate Slug", "CATALOG-LAPTOP-PHASE3", "OTHER-001", "Other", "20.00", null, uuid(category), true));
        assertThat(duplicateSlug.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateSlug.getBody().path("code").asText()).isEqualTo("DUPLICATE_SLUG");
        ResponseEntity<JsonNode> invalidPrice = postAuth("/api/v1/staff/products", staff,
                product("Bad Price", "bad-price-phase3", "BAD-PRICE", "Other", "10.00", "11.00", uuid(category), true));
        assertThat(invalidPrice.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidPrice.getBody().path("code").asText()).isEqualTo("INVALID_PRICE");

        ResponseEntity<JsonNode> updated = putAuth("/api/v1/staff/products/" + uuid(created), staff,
                product("Updated Laptop", "catalog-laptop-phase3", "lap-001", "CoreBrand", "1100.00", null,
                        uuid(category), true));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().path("name").asText()).isEqualTo("Updated Laptop");
        assertThat(updated.getBody().path("effectivePrice").decimalValue()).isEqualByComparingTo("1100.00");

        String admin = roleAccess("product.admin@example.com", UserRole.ADMIN);
        JsonNode adminProduct = createProduct(admin, "Admin Product", "admin-product-phase3", "ADMIN-PROD-1",
                "AdminBrand", "25.00", null, uuid(category), true);
        assertThat(adminProduct.path("active").asBoolean()).isTrue();

        assertThat(postAuth("/api/v1/staff/products/" + uuid(created) + "/deactivate", staff, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/v1/products/catalog-laptop-phase3", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode publicPage = rest.getForObject("/api/v1/products?q=Updated%20Laptop", JsonNode.class);
        assertThat(publicPage.path("totalElements").asLong()).isZero();

        assertThat(postAuth("/api/v1/staff/categories/" + uuid(category) + "/deactivate", staff, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/v1/products/admin-product-phase3", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void productMediaIsOrderedAndOnlyOnePrimaryIsAllowed() {
        String staff = roleAccess("media.staff@example.com", UserRole.STAFF);
        JsonNode category = createCategory(staff, "Media Category", "media-category-phase3", null, true);
        JsonNode product = createProduct(staff, "Media Product", "media-product-phase3", "MEDIA-001",
                "MediaBrand", "50.00", null, uuid(category), true);
        UUID productId = uuid(product);

        JsonNode second = postAuth("/api/v1/staff/products/" + productId + "/media", staff,
                media("https://cdn.example.invalid/two.jpg", "Second image", 2, false)).getBody();
        JsonNode first = postAuth("/api/v1/staff/products/" + productId + "/media", staff,
                media("/catalog/one.jpg", "First image", 1, true)).getBody();
        JsonNode detail = rest.getForObject("/api/v1/products/media-product-phase3", JsonNode.class);
        assertThat(detail.path("media").get(0).path("id").asText()).isEqualTo(first.path("id").asText());
        assertThat(detail.path("media").get(1).path("id").asText()).isEqualTo(second.path("id").asText());

        JsonNode third = postAuth("/api/v1/staff/products/" + productId + "/media", staff,
                media("/catalog/three.jpg", "New primary", 3, true)).getBody();
        detail = rest.getForObject("/api/v1/products/media-product-phase3", JsonNode.class);
        assertThat(primaryCount(detail.path("media"))).isEqualTo(1);
        assertThat(detail.path("media").get(2).path("id").asText()).isEqualTo(third.path("id").asText());

        ResponseEntity<JsonNode> duplicateOrder = postAuth("/api/v1/staff/products/" + productId + "/media", staff,
                media("/catalog/duplicate.jpg", "Duplicate", 3, false));
        assertThat(duplicateOrder.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThatThrownBy(() -> jdbc.update("insert into product_media(id, product_id, media_url, alt_text, display_order, is_primary) values (?, ?, ?, ?, ?, true)",
                UUID.randomUUID(), productId, "/catalog/db-conflict.jpg", "DB conflict", 9))
                .hasMessageContaining("uq_product_media_one_primary");
    }

    @Test
    void publicSearchSupportsTextSubtreeBrandPriceAndCombinedFilters() {
        String staff = roleAccess("search.staff@example.com", UserRole.STAFF);
        JsonNode root = createCategory(staff, "Search Electronics", "search-electronics", null, true);
        JsonNode laptops = createCategory(staff, "Search Laptops", "search-laptops", uuid(root), true);
        JsonNode books = createCategory(staff, "Search Books", "search-books", null, true);
        createProduct(staff, "Orion Laptop", "orion-laptop", "ORION-100", "Nova", "1000.00", "800.00", uuid(laptops), true);
        createProduct(staff, "Nova Desktop", "nova-desktop", "NOVA-200", "Nova", "600.00", null, uuid(root), true);
        createProduct(staff, "Cooking Guide", "cooking-guide", "BOOK-100", "PaperHouse", "30.00", null, uuid(books), true);
        createProduct(staff, "Hidden Laptop", "hidden-laptop", "HIDDEN-100", "Nova", "700.00", null, uuid(laptops), false);

        assertTotal("/api/v1/products?q=orion", 1);
        assertTotal("/api/v1/products?q=NOVA-200", 1);
        assertTotal("/api/v1/products?category=search-electronics", 2);
        assertTotal("/api/v1/products?brand=nova", 2);
        assertTotal("/api/v1/products?minPrice=500&maxPrice=850", 2);
        assertTotal("/api/v1/products?category=search-electronics&brand=Nova&minPrice=750&maxPrice=900", 1);
        assertTotal("/api/v1/products?q=laptop", 1);
    }

    @Test
    void pagingAndWhitelistedSortingAreBoundedAndDeterministic() {
        String staff = roleAccess("paging.staff@example.com", UserRole.STAFF);
        JsonNode category = createCategory(staff, "Paging Category", "paging-category", null, true);
        JsonNode alpha = createProduct(staff, "Alpha", "paging-alpha", "PAGE-A", "PagingBrand", "30.00", null, uuid(category), true);
        JsonNode beta = createProduct(staff, "Beta", "paging-beta", "PAGE-B", "PagingBrand", "10.00", null, uuid(category), true);
        JsonNode gamma = createProduct(staff, "Gamma", "paging-gamma", "PAGE-C", "PagingBrand", "20.00", "15.00", uuid(category), true);
        jdbc.update("update products set created_at = timestamp with time zone '2025-01-01 00:00:00+00' where id = ?", uuid(alpha));
        jdbc.update("update products set created_at = timestamp with time zone '2025-02-01 00:00:00+00' where id = ?", uuid(beta));
        jdbc.update("update products set created_at = timestamp with time zone '2025-03-01 00:00:00+00' where id = ?", uuid(gamma));

        JsonNode firstPage = get("/api/v1/products?brand=PagingBrand&sort=price-asc&page=0&size=2");
        assertThat(firstPage.path("content")).hasSize(2);
        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("first").asBoolean()).isTrue();
        assertThat(firstPage.path("content").get(0).path("name").asText()).isEqualTo("Beta");
        assertThat(firstPage.path("content").get(1).path("name").asText()).isEqualTo("Gamma");
        assertThat(get("/api/v1/products?brand=PagingBrand&sort=price-desc").path("content").get(0).path("name").asText())
                .isEqualTo("Alpha");
        assertThat(get("/api/v1/products?brand=PagingBrand&sort=name").path("content").get(0).path("name").asText())
                .isEqualTo("Alpha");
        assertThat(get("/api/v1/products?brand=PagingBrand&sort=newest").path("content").get(0).path("name").asText())
                .isEqualTo("Gamma");
        assertThat(rest.getForEntity("/api/v1/products?size=101", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rest.getForEntity("/api/v1/products?sort=createdAt", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private JsonNode createCategory(String token, String name, String slug, UUID parentId, boolean active) {
        ResponseEntity<JsonNode> response = postAuth("/api/v1/staff/categories", token,
                category(name, slug, parentId, active));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
    private JsonNode createProduct(String token, String name, String slug, String sku, String brand,
                                   String basePrice, String salePrice, UUID categoryId, boolean active) {
        ResponseEntity<JsonNode> response = postAuth("/api/v1/staff/products", token,
                product(name, slug, sku, brand, basePrice, salePrice, categoryId, active));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
    private Map<String, Object> category(String name, String slug, UUID parentId, boolean active) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name); body.put("slug", slug); body.put("description", name + " description");
        if (parentId != null) body.put("parentCategoryId", parentId); body.put("active", active); return body;
    }
    private Map<String, Object> product(String name, String slug, String sku, String brand, String basePrice,
                                        String salePrice, UUID categoryId, boolean active) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name); body.put("slug", slug); body.put("description", name + " searchable description");
        body.put("sku", sku); body.put("brand", brand); body.put("basePrice", new BigDecimal(basePrice));
        if (salePrice != null) body.put("salePrice", new BigDecimal(salePrice));
        body.put("categoryId", categoryId); body.put("active", active); return body;
    }
    private Map<String, Object> media(String url, String alt, int order, boolean primary) {
        return Map.of("mediaUrl", url, "altText", alt, "displayOrder", order, "primary", primary);
    }
    private String roleAccess(String email, UserRole role) {
        UserAccount user = new UserAccount(email, passwordEncoder.encode(PASSWORD), "Catalog Role", null, role);
        user.verifyEmail(); users.saveAndFlush(user);
        JsonNode login = rest.postForObject("/api/v1/auth/login", Map.of("email", email, "password", PASSWORD), JsonNode.class);
        return login.path("accessToken").asText();
    }
    private ResponseEntity<JsonNode> postAuth(String path, String token, Object body) {
        return exchangeAuth(HttpMethod.POST, path, token, body);
    }
    private ResponseEntity<JsonNode> putAuth(String path, String token, Object body) {
        return exchangeAuth(HttpMethod.PUT, path, token, body);
    }
    private ResponseEntity<JsonNode> exchangeAuth(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }
    private JsonNode get(String path) { return rest.getForObject(path, JsonNode.class); }
    private void assertTotal(String path, long expected) { assertThat(get(path).path("totalElements").asLong()).isEqualTo(expected); }
    private UUID uuid(JsonNode node) { return UUID.fromString(node.path("id").asText()); }
    private long primaryCount(JsonNode media) { long count = 0; for (JsonNode item : media) if (item.path("primary").asBoolean()) count++; return count; }
}
