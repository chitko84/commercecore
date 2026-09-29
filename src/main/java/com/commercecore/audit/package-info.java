/**
 * Audit logging for administrative and sensitive actions.
 *
 * Phase 8 implementation: every admin account action, review moderation
 * decision and review deletion is recorded as an append-only audit entry
 * that administrators can query for accountability and reporting.
 */
package com.commercecore.audit;
