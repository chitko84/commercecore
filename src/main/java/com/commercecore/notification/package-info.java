/**
 * Customer and system notifications (in-app).
 *
 * Phase 8 implementation: a customer notification center fed by order
 * lifecycle and refund events. Notifications are created in-process
 * (no Kafka/Redis yet); Phase 9 may add async delivery adapters.
 */
package com.commercecore.notification;
