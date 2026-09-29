/**
 * Stock levels, inventory tracking, and concurrency-safe reservation.
 *
 * Owns XOR-scoped inventory, reservation lifecycles, immutable movements,
 * derived availability, and pessimistic-lock stock mutation.
 */
package com.commercecore.inventory;
