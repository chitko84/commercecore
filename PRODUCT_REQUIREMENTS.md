# PRODUCT_REQUIREMENTS.md

## What CommerceCore is

CommerceCore is a portfolio-quality, enterprise-style e-commerce platform.
V1 scope covers: auth/users, catalog, categories, variants, inventory,
cart, wishlist, promotions, checkout, orders, shipping, payments/refunds,
reviews, notifications, admin, audit/reporting, Kafka, Redis, selected
microservices, observability, frontend, testing, and deployment.

## Roles

- **CUSTOMER** -- browses/buys; owns their own cart, orders, reviews,
  addresses.
- **STAFF** -- operational access (e.g. order/inventory handling) short
  of full admin rights.
- **ADMIN** -- full administrative access.

Exact permission boundaries are defined when RBAC is implemented
(Phase 2); this is the approved role set to design against.

## Core V1 domains

- Users / auth
- Addresses
- Products / categories / variants
- Inventory
- Cart / wishlist / promotions
- Checkout / orders / shipping
- Payments / refunds
- Reviews / notifications
- Admin / audit / reports
- Kafka / Redis / selected microservices / observability
- Frontend / testing / deployment

## Important future business rules

These are approved constraints for whichever phase implements the
related domain -- recorded now so they aren't lost or reinvented
later:

- **DTO/entity separation** -- JPA entities never cross the API
  boundary (see `ARCHITECTURE.md`).
- **Inventory reservation + release** -- stock is reserved on
  checkout start and released on cancel/expiry/failure, not decremented
  irreversibly up front.
- **Concurrency must prevent negative stock** -- concurrent
  purchases/reservations must never drive stock below zero.
- **Order state transitions** -- orders move through a defined,
  enforced state machine (e.g. created -> paid -> fulfilled/shipped ->
  completed, with cancellation/refund branches); invalid transitions
  are rejected.
- **Order price/address snapshots** -- orders capture price and
  shipping/billing address at the time of purchase; later changes to a
  product's price or a customer's saved address must not retroactively
  alter past orders.
- **Payment idempotency** -- payment operations are safe to retry
  without double-charging.
- **Full/partial refunds** -- refunds support both full and partial
  amounts against a payment/order.
- **Verified-purchase reviews** -- a review is only postable by a
  customer who actually purchased the reviewed product.
- **RBAC** -- endpoints/actions are authorized per the CUSTOMER /
  STAFF / ADMIN roles above.
- **Audit logging** -- administrative and other sensitive actions are
  recorded for accountability/reporting.

## Frontend groups

- **Public store** -- browsing, product/category pages, unauthenticated
  flows.
- **Customer area** -- account, orders, cart/wishlist, reviews.
- **Staff/Admin area** -- operational and administrative screens.

Visual style/UI direction is documented separately in
`docs/UI_DIRECTION.md` -- do not duplicate that detail here.

## Fixed 10-phase roadmap

All approved CommerceCore V1 work must fit inside these 10 phases. Do not
add phases or move work between phases without an explicit decision
recorded in `DECISIONS.md`.

1. Foundation + Architecture
2. Users + Authentication + Security
3. Product Catalog + Categories + Search
4. Variants + Inventory + Concurrency
5. Cart + Wishlist + Promotions
6. Checkout + Orders + Shipping
7. Payments + Refunds + Reliability
8. Reviews + Notifications + Admin + Audit + Reports
9. Kafka + Redis + Microservices + Observability
10. Frontend + Integration + Testing + Deployment

## Status

See `PROJECT_STATE.md` for the current phase and pass/fail status, and
`NEXT_TASK.md` for the next unit of work.
