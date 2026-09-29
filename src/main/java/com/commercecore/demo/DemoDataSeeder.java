package com.commercecore.demo;

import com.commercecore.cart.CartDtos;
import com.commercecore.cart.CartService;
import com.commercecore.category.CategoryDtos;
import com.commercecore.category.CategoryService;
import com.commercecore.inventory.InventoryDtos;
import com.commercecore.inventory.InventoryService;
import com.commercecore.order.OrderDtos;
import com.commercecore.order.OrderService;
import com.commercecore.payment.PaymentDtos;
import com.commercecore.payment.PaymentMethod;
import com.commercecore.payment.PaymentService;
import com.commercecore.shipping.ShippingServiceLevel;
import com.commercecore.product.ProductDtos;
import com.commercecore.product.ProductMediaService;
import com.commercecore.product.ProductService;
import com.commercecore.product.ProductVariantService;
import com.commercecore.product.VariantDtos;
import com.commercecore.promotion.PromotionDtos;
import com.commercecore.promotion.PromotionService;
import com.commercecore.promotion.PromotionScope;
import com.commercecore.promotion.PromotionType;
import com.commercecore.user.AddressService;
import com.commercecore.user.UserAccount;
import com.commercecore.user.UserAccountRepository;
import com.commercecore.user.UserDtos;
import com.commercecore.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deterministic local/demo fixture data, gated behind {@code app.demo.seed=true}
 * (set via APP_DEMO_SEED in the demo Docker Compose profile). Never runs in
 * production or in the test profile. Idempotent: it skips when the demo
 * accounts already exist, so restarts never duplicate data.
 *
 * Seeds: categories, products with variants/media/inventory, two promotions,
 * a demo CUSTOMER / STAFF / ADMIN account, an address, and one DELIVERED,
 * fully paid order for the demo customer so the review flow (verified
 * purchase = DELIVERED) can be exercised end to end.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String DEMO_CUSTOMER_EMAIL = "demo@commercecore.dev";
    public static final String DEMO_STAFF_EMAIL = "staff@commercecore.dev";
    public static final String DEMO_ADMIN_EMAIL = "admin@commercecore.dev";
    public static final String DEMO_PASSWORD = "DemoPass!2026";
    public static final String STAFF_PASSWORD = "StaffPass!2026";
    public static final String ADMIN_PASSWORD = "AdminPass!2026";

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final CategoryService categories;
    private final ProductService products;
    private final ProductMediaService media;
    private final ProductVariantService variants;
    private final InventoryService inventory;
    private final PromotionService promotions;
    private final AddressService addresses;
    private final CartService carts;
    private final OrderService orders;
    private final PaymentService payments;

    public DemoDataSeeder(UserAccountRepository users, PasswordEncoder encoder,
                          CategoryService categories, ProductService products,
                          ProductMediaService media, ProductVariantService variants,
                          InventoryService inventory, PromotionService promotions,
                          AddressService addresses, CartService carts,
                          OrderService orders, PaymentService payments) {
        this.users = users;
        this.encoder = encoder;
        this.categories = categories;
        this.products = products;
        this.media = media;
        this.variants = variants;
        this.inventory = inventory;
        this.promotions = promotions;
        this.addresses = addresses;
        this.carts = carts;
        this.orders = orders;
        this.payments = payments;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.findByEmail(DEMO_CUSTOMER_EMAIL).isPresent()) {
            log.info("Demo data already seeded — skipping ({} exists).", DEMO_CUSTOMER_EMAIL);
            return;
        }
        log.info("Seeding CommerceCore demo data…");
        UserAccount admin = user(DEMO_ADMIN_EMAIL, ADMIN_PASSWORD, "Ada Admin", UserRole.ADMIN);
        UserAccount staff = user(DEMO_STAFF_EMAIL, STAFF_PASSWORD, "Sam Staff", UserRole.STAFF);
        UserAccount customer = user(DEMO_CUSTOMER_EMAIL, DEMO_PASSWORD, "Dana Demo", UserRole.CUSTOMER);

        CategoryDtos.CategoryResponse audio = category("Audio", "audio", "Headphones, speakers and sound", admin.getId());
        CategoryDtos.CategoryResponse wearables = category("Wearables", "wearables", "Watches, bands and earbuds", admin.getId());
        CategoryDtos.CategoryResponse accessories = category("Accessories", "accessories", "Keyboards, mice and input", admin.getId());
        CategoryDtos.CategoryResponse homeTech = category("Home Tech", "home-tech", "Displays and smart home", admin.getId());
        UUID audioId = audio.id();
        UUID wearablesId = wearables.id();
        UUID accessoriesId = accessories.id();
        UUID homeTechId = homeTech.id();

        UUID headphones = product("Aurora Wireless Headphones", "aurora-headphones", "AU-001", "Aurora",
                "Over-ear wireless headphones with active noise cancellation, 40-hour battery life and premium comfort.",
                "199.00", null, audioId, "/products/headphones.jpg", "Aurora wireless headphones", admin.getId());
        UUID speaker = product("Bassline Portable Speaker", "bassline-portable-speaker", "BS-002", "Bassline",
                "Rugged 360° Bluetooth speaker with deep bass, IP67 waterproofing and 24 hours of playtime.",
                "129.00", "99.00", audioId, "/products/speaker.jpg", "Bassline portable speaker", admin.getId());
        UUID watch = product("Pulse Smartwatch", "pulse-smartwatch", "PW-003", "Pulse",
                "Health-first smartwatch with heart-rate, sleep and workout tracking plus a 10-day battery.",
                "249.00", null, wearablesId, "/products/smartwatch.jpg", "Pulse smartwatch", admin.getId());
        UUID earbuds = product("Echo True Wireless Earbuds", "echo-true-wireless-earbuds", "EC-004", "Echo",
                "Compact true wireless earbuds with adaptive ANC, wireless charging and multi-device pairing.",
                "159.00", null, wearablesId, "/products/earbuds.jpg", "Echo true wireless earbuds", admin.getId());
        UUID keyboard = product("Keystroke Mechanical Keyboard", "keystroke-mechanical-keyboard", "KK-005", "Keystroke",
                "Hot-swappable 75% mechanical keyboard with gasket mount, PBT keycaps and per-key RGB.",
                "119.00", "99.00", accessoriesId, "/products/keyboard.jpg", "Keystroke mechanical keyboard", admin.getId());
        UUID mouse = product("Glide Wireless Mouse", "glide-wireless-mouse", "GL-006", "Glide",
                "Ultra-light 59g wireless mouse with 26K DPI optical sensor and 90-hour battery.",
                "79.00", null, accessoriesId, "/products/mouse.jpg", "Glide wireless mouse", admin.getId());
        UUID monitor = product("Vista 27\" 4K Monitor", "vista-27-4k-monitor", "VS-007", "Vista",
                "27-inch 4K IPS display with USB-C 90W delivery, factory-calibrated colors and slim bezels.",
                "499.00", "449.00", homeTechId, "/products/monitor.jpg", "Vista 27 inch 4K monitor", admin.getId());
        UUID camera = product("Capture Mirrorless Camera", "capture-mirrorless-camera", "CP-008", "Capture",
                "Compact full-frame mirrorless camera with 33MP sensor, 4K60 video and in-body stabilization.",
                "1299.00", null, homeTechId, "/products/camera.jpg", "Capture mirrorless camera", admin.getId());

        UUID deliveredVariant = variant(headphones, "AU-001-BLK", "Midnight", admin.getId(), 24);
        variant(headphones, "AU-001-SND", "Sand", admin.getId(), 18);
        variant(watch, "PW-003-BLK", "Midnight", admin.getId(), 30);
        variant(watch, "PW-003-SLV", "Silver", admin.getId(), 15);
        variant(keyboard, "KK-005-WHT", "Arctic White", admin.getId(), 26);
        variant(keyboard, "KK-005-BLK", "Graphite", admin.getId(), 20);
        variant(earbuds, "EC-004-WHT", "Pearl", admin.getId(), 40);
        variant(earbuds, "EC-004-BLK", "Charcoal", admin.getId(), 35);

        inventory.initialize(new InventoryDtos.InitializeRequest(speaker, null, 60, 8));
        inventory.initialize(new InventoryDtos.InitializeRequest(mouse, null, 80, 10));
        inventory.initialize(new InventoryDtos.InitializeRequest(monitor, null, 25, 4));
        inventory.initialize(new InventoryDtos.InitializeRequest(camera, null, 12, 3));

        promotion("SAVE10", "10% off your order", PromotionType.PERCENTAGE,
                new BigDecimal("10.00"), BigDecimal.ZERO, admin.getId());
        promotion("FREESHIP", "Free standard shipping", PromotionType.FREE_SHIPPING,
                null, new BigDecimal("50.00"), admin.getId());

        UserDtos.AddressResponse address = addresses.create(customer.getId(), new UserDtos.AddressRequest(
                "Home", "Dana Demo", "+60123456789", "12 Jalan Damai", "Unit 4B",
                "Kuala Lumpur", "Wilayah Persekutuan", "50400", "MY", true, true));

        // A DELIVERED, fully paid order so the demo customer can write a verified-purchase review.
        carts.add(customer.getId(), new CartDtos.AddItemRequest(headphones, deliveredVariant, 1));
        OrderDtos.OrderResponse order = orders.checkout(customer.getId(), "demo-checkout-delivered-0001",
                new OrderDtos.CheckoutRequest(address.id(), null, ShippingServiceLevel.STANDARD));
        payments.pay(customer.getId(), order.id(), "demo-payment-0001",
                new PaymentDtos.PaymentRequest(PaymentMethod.CARD, null));
        orders.processing(admin.getId(), order.id());
        orders.createShipment(admin.getId(), order.id(),
                new OrderDtos.ShipmentRequest("DemoCarrier", "DEMO-TRACK-0001", null));
        orders.ship(admin.getId(), order.id());
        orders.deliver(admin.getId(), order.id());

        log.info("Demo data ready: customer {}, staff {}, admin {}, delivered order {}.",
                DEMO_CUSTOMER_EMAIL, DEMO_STAFF_EMAIL, DEMO_ADMIN_EMAIL, order.orderNumber());
    }

    private UserAccount user(String email, String password, String name, UserRole role) {
        UserAccount account = new UserAccount(email, encoder.encode(password), name, null, role);
        account.verifyEmail();
        return users.saveAndFlush(account);
    }

    private CategoryDtos.CategoryResponse category(String name, String slug, String description, UUID actor) {
        return categories.create(actor, new CategoryDtos.CategoryRequest(name, slug, description, null, true));
    }

    private UUID product(String name, String slug, String sku, String brand, String description,
                         String basePrice, String salePrice, UUID categoryId, String mediaUrl,
                         String altText, UUID actor) {
        ProductDtos.ProductResponse created = products.create(actor, new ProductDtos.ProductRequest(
                name, slug, description, sku, brand, money(basePrice),
                salePrice == null ? null : money(salePrice), categoryId, true));
        media.create(created.id(), new ProductDtos.MediaRequest(mediaUrl, altText, 0, true));
        return created.id();
    }

    private UUID variant(UUID productId, String sku, String value, UUID actor, long quantity) {
        VariantDtos.VariantResponse created = variants.create(actor, productId, new VariantDtos.VariantRequest(
                sku, null, true, List.of(new VariantDtos.AttributeRequest("Color", value, 0))));
        inventory.initialize(new InventoryDtos.InitializeRequest(null, created.id(), quantity, 5));
        return created.id();
    }

    private void promotion(String code, String name, PromotionType type,
                           BigDecimal value, BigDecimal minimumSpend, UUID actor) {
        promotions.create(actor, new PromotionDtos.PromotionRequest(
                code, name, "Seeded demo promotion", type, PromotionScope.CART,
                value, minimumSpend, null,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(7L * 24 * 3600),
                null, null, true, null, null));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
