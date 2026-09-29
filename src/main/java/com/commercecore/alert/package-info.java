/**
 * Operational alerts for administrators.
 *
 * Phase 8 implementation: transition-based, deduplicated alerts
 * (LOW_STOCK_ALERT and FAILED_PAYMENT_ALERT). Alerts are only produced by
 * durable state transitions -- never by reads -- and each distinct event is
 * recorded once (dedup key). Administrators list and acknowledge alerts.
 */
package com.commercecore.alert;