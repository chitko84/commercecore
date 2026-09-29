/**
 * Product reviews and ratings.
 *
 * Phase 8 implementation: verified-purchase reviews (only customers whose
 * order for the product has been DELIVERED may review), a PENDING ->
 * APPROVED / REJECTED moderation workflow (new and edited reviews start
 * PENDING; only APPROVED reviews are public and count toward ratings),
 * per-customer review management and STAFF/ADMIN moderation with audit
 * records.
 */
package com.commercecore.review;