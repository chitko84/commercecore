package com.commercecore.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "addresses")
public class Address {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @Column(nullable = false, length = 50) private String label;
    @Column(name = "recipient_name", nullable = false, length = 150) private String recipientName;
    @Column(nullable = false, length = 32) private String phone;
    @Column(name = "address_line_1", nullable = false, length = 200) private String addressLine1;
    @Column(name = "address_line_2", length = 200) private String addressLine2;
    @Column(nullable = false, length = 100) private String city;
    @Column(name = "state_region", nullable = false, length = 100) private String stateRegion;
    @Column(name = "postal_code", nullable = false, length = 20) private String postalCode;
    @Column(name = "country_code", nullable = false, length = 2) private String countryCode;
    @Column(name = "default_shipping", nullable = false) private boolean defaultShipping;
    @Column(name = "default_billing", nullable = false) private boolean defaultBilling;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Address() { }

    public Address(UserAccount user, String label, String recipientName, String phone,
                   String addressLine1, String addressLine2, String city, String stateRegion,
                   String postalCode, String countryCode, boolean defaultShipping, boolean defaultBilling) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        update(label, recipientName, phone, addressLine1, addressLine2, city, stateRegion,
                postalCode, countryCode, defaultShipping, defaultBilling);
    }

    public void update(String label, String recipientName, String phone, String addressLine1,
                       String addressLine2, String city, String stateRegion, String postalCode,
                       String countryCode, boolean defaultShipping, boolean defaultBilling) {
        this.label = required(label, "label");
        this.recipientName = required(recipientName, "recipientName");
        this.phone = required(phone, "phone");
        this.addressLine1 = required(addressLine1, "addressLine1");
        this.addressLine2 = optional(addressLine2);
        this.city = required(city, "city");
        this.stateRegion = required(stateRegion, "stateRegion");
        this.postalCode = required(postalCode, "postalCode");
        this.countryCode = required(countryCode, "countryCode").toUpperCase(Locale.ROOT);
        this.defaultShipping = defaultShipping;
        this.defaultBilling = defaultBilling;
    }

    public void makeDefaultShipping() { defaultShipping = true; }
    public void makeDefaultBilling() { defaultBilling = true; }

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getLabel() { return label; }
    public String getRecipientName() { return recipientName; }
    public String getPhone() { return phone; }
    public String getAddressLine1() { return addressLine1; }
    public String getAddressLine2() { return addressLine2; }
    public String getCity() { return city; }
    public String getStateRegion() { return stateRegion; }
    public String getPostalCode() { return postalCode; }
    public String getCountryCode() { return countryCode; }
    public boolean isDefaultShipping() { return defaultShipping; }
    public boolean isDefaultBilling() { return defaultBilling; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
