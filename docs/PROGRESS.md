# Progress — vsnvi-food

Tick items once they are merged. Every module follows the same definition of done:
**migration → domain → infrastructure → application → api → tests**.

Modules are listed in build order: a module comes after every module it calls or listens to
(see the dependency graph in [ARCHITECTURE.md §2](ARCHITECTURE.md#2-modules)).

## Phase 0 — Foundation

- [ ] Project docs and Claude rules (architecture, API design, database design, security, ADR-001 to ADR-005)
- [x] `application.yaml` profiles (`local`, `test`, `prod`) with Postgres and Redis config from env
- [x] `compose.yaml` for local Postgres, Redis and Mailpit; `.env.example`
- [ ] Dependencies: Spring Modulith 2.x BOM (`spring-modulith-starter-core`, `-starter-jpa`, `-starter-test`), Redis, WebSocket, jjwt (`jjwt-api`, `jjwt-impl`, a JSON module; see ADR-002), Razorpay SDK (or HTTP client), Testcontainers Redis
  - [x] Spring Modulith 2.1.2 BOM and starters; jjwt 0.13.0 with `jjwt-gson` (ADR-002)
  - [ ] Redis, WebSocket, Razorpay, Testcontainers Redis
- [ ] `common` kernel
  - [x] `BaseEntity` (UUID id, `createdAt`, `updatedAt`; no `version`, see `.claude/rules/java.md`)
    - [x] `@DataJpaTest` for audit timestamps from a fixed `Clock` (`UserPersistenceTest`)
  - [x] `Money` value object and JPA mapping
  - [x] `DomainException` hierarchy and `ErrorCode`
  - [x] Global `@RestControllerAdvice` returning `ProblemDetail`
  - [ ] Pagination envelopes (offset and cursor)
    - [x] Offset: `PageResponse`
  - [ ] Trace ID filter (MDC)
  - [ ] Idempotency-Key support
  - [x] `Clock` bean (UTC)
  - [ ] Event Publication Registry: Flyway migration for `event_publication` (copied from the Modulith version's schema), `@EnableAsync`, republish on restart, completion cleanup
    - [x] `event_publication` migration (`V1__common_create_event_publication.sql`, from Modulith 2.1.2)
- [ ] OpenAPI / Swagger UI (springdoc)
  - [ ] `OpenApiConfig`: info, servers, `bearerAuth` security scheme
  - [ ] One `GroupedOpenApi` per module
  - [ ] `OpenApiCustomizer`: shared `ProblemDetail` schema and default error responses
  - [ ] `@IdempotencyKeyHeader` meta-annotation
  - [ ] Profile config: enabled in `local`/`dev`, disabled in `prod`; security allowlist in non-prod
  - [ ] CI: export `target/openapi.json` as a build artifact
- [ ] Flyway baseline migration
- [ ] Testcontainers integration test base (Postgres and Redis)
- [x] Spring Modulith
  - [x] `ApplicationModules.verify()` test, `common` as an open module
  - [x] `@NamedInterface("api")` conventions and the check that `api.web` isn't exposed (`ModularityTests`)
- [ ] CI pipeline (`./mvnw verify`)

## Phase 1 — Users, auth, restaurants, menus

### user
- [x] Migration: users, addresses
- [x] Domain: `User`, `Address`, account status
- [x] Public facade: `UserApi` (incl. `createUser` for registration, address lookup with coordinates)
- [x] Event: `UserBlocked`
- [x] API: `/users/me`, `/users/me/addresses`
- [x] Admin: `GET /admin/users`, `POST /admin/users/{id}/block`
- [x] Tests

### auth
- [ ] Migration: credentials, refresh_tokens
- [x] Security filter chain, exact-path public allowlist, `CurrentUser`, ProblemDetail 401/403 (in `common`, ADR-006)
- [ ] `JwtService` (jjwt) implementing `TokenAuthenticator`
- [ ] Register, login (rejects blocked users), refresh (rotation and reuse detection), logout
- [ ] Login rate limiting (per IP and per account)
- [ ] `AdminBootstrap`: first admin from `ADMIN_BOOTSTRAP_*` env vars
- [ ] Event: `UserRegistered`
- [ ] `TokenAuthenticator` implementation for STOMP `CONNECT` (uses `JwtService`)
- [ ] Listener: `UserBlocked` revokes refresh tokens and calls `StompSessionRegistry.closeSessionsOf`
- [ ] Tests

### restaurant
- [ ] Migration: restaurants, opening_hours
- [ ] Domain: `Restaurant`, `OpeningHours`, serviceable radius, approval status, `deliveryFee`, `packagingCharge`
- [ ] Public facade: `RestaurantApi`
- [ ] API: `/restaurants` (search by location, cuisine and rating), open/close
- [ ] Admin: list, approve and suspend restaurants (`/admin/restaurants`)
- [ ] Tests

### menu
- [ ] Migration: menu_categories, menu_items, item_variants, item_add_ons
- [ ] Domain: `MenuItem`, `Variant`, `AddOn`, availability
- [ ] Public facade: `MenuApi` (pricing lookup for cart and order)
- [ ] API: `/restaurants/{id}/menu`, `/menu-items`, `/menu-categories`
- [ ] Tests

## Phase 2 — Promotions, cart, payments, orders

### promotion
- [ ] Migration: coupons, coupon_redemptions
- [ ] Domain: `Coupon` (FLAT, PERCENT with cap, FREE_DELIVERY), rules (validity, min subtotal, per-customer/total limits, first order), `CouponRedemption`
- [ ] Public facade: `PromotionApi` (`evaluate`, `redeem` with row lock, `release`)
- [ ] API: admin platform coupons, owner restaurant coupons, available coupons for customers
- [ ] Tests (every rule, limit concurrency, release on cancel)

### cart
- [ ] Migration: carts, cart_items
- [ ] Domain: single-restaurant rule
- [ ] `CartPricing`: subtotal, packaging, delivery fee, coupon discount (amount from `PromotionApi`), tax (`pricing.tax-percent`)
- [ ] Public facade: `CartApi` (priced cart, `clear` at checkout)
- [ ] API: `/cart`, `/cart/coupon`
- [ ] Tests

### payment
- [ ] Migration: payments, refunds, processed_webhooks
- [ ] Public facade: `PaymentApi` (`createPayment` incl. COD, `openGatewayOrder` after commit, `cancelPayment` (refund or void), `markCollected`)
- [ ] `PaymentGateway` interface and `FakePaymentGateway`
- [ ] `RazorpayGateway`: create order, verify signatures, refund
- [ ] Webhook endpoint: raw-body HMAC verification, event-ID dedupe in the same transaction
- [ ] Events: `PaymentSucceeded`, `PaymentFailed`, `RefundIssued`
- [ ] Refunds and voids via `PaymentApi.cancelPayment`, refund webhooks, auto-refund of late captures (the only place they are refunded)
- [ ] Cash on delivery flow (`PENDING_COLLECTION` → `COLLECTED`)
- [ ] API: `/payments/*`
- [ ] Tests

### order
- [ ] Migration: orders (incl. address snapshot and `partner_user_id`), order_items, order_status_history
- [ ] Domain: `Order` aggregate, state machine, pricing snapshot, address snapshot
- [ ] Public facade: `OrderApi` (`markPartnerAssigned`, `markPickedUp`, `markDelivered`, ownership and location reads)
- [ ] Service: checkout, cancel, owner actions
- [ ] Events: `OrderPlaced`, `OrderAccepted`, `OrderRejected`, `OrderReadyForPickup`, `OrderPickedUp`, `OrderDelivered`, `OrderCancelled`
- [ ] Listeners: `PaymentSucceeded` (ignored unless `PAYMENT_PENDING`), `PaymentFailed` (no state change), `RefundIssued`
- [ ] Checkout: pricing snapshot from `CartApi`, `PromotionApi.redeem` (passes `isFirstOrder`), `CartApi.clear`, then `PaymentApi.openGatewayOrder` after commit
- [ ] Calls `PaymentApi.cancelPayment` and `PromotionApi.release` on reject, cancel and timeout, and `PaymentApi.markCollected` on COD delivery
- [ ] Payment timeout job: cancels orders stuck in `PAYMENT_PENDING` for 15 minutes
- [ ] API: `/orders`, `/users/me/orders`, `/restaurants/{id}/orders`
- [ ] Tests (every allowed and forbidden transition)

## Phase 3 — Delivery and real-time tracking

### delivery
- [ ] Migration: delivery_partners, delivery_tasks, delivery_location_history
- [ ] Domain: `DeliveryTask` lifecycle, partner availability, partner approval status
- [ ] Listener: `UserRegistered` (role `RIDER`) creates a `PENDING_APPROVAL` profile
- [ ] Admin: list, approve and suspend partners (`/admin/delivery-partners`)
- [ ] Redis GEO: partner availability index, latest-location hash, sweeper for expired heartbeats
- [ ] Assignment: `GEOSEARCH` nearest partner, offer timeout, fallback, offer lock
- [ ] `DeliveryAssignmentStalled` alert after `delivery.assignment.alert-after`; maximum search radius
- [ ] Delivery OTP: generate with `SecureRandom`, show only to the customer in the tracking snapshot, verify on delivery (5 attempts), clear when the task ends
- [ ] Listeners: `OrderReadyForPickup` starts assignment, `OrderCancelled` cancels the task
- [ ] Event: `DeliveryAssigned`; calls `OrderApi.markPartnerAssigned`, `markPickedUp`, `markDelivered`
- [ ] API: `/delivery/availability`, `/delivery/location` (rate-limited), `/delivery-tasks/*`
- [ ] Tests

### tracking (WebSocket STOMP)
- [ ] STOMP config in `common` (`/ws`, `/app`, `/topic`, `/user/queue`), the `TokenAuthenticator` and `SubscriptionAuthorizer` SPIs, and `StompSessionRegistry` with an expiry sweep
- [ ] `SubscriptionAuthorizer` implementations (`delivery` for tracking, `notification` for the restaurant dashboard)
- [ ] `TrackingPublisher` (in `delivery`): status pushes from `READY_FOR_PICKUP`, plus location and ETA once assigned
- [ ] `EtaCalculator` (haversine)
- [ ] Sampled location history persistence
- [ ] Tracking snapshot endpoint `GET /orders/{id}/tracking`
- [ ] Tests

## Phase 4 — Notifications and admin

### notification
- [ ] Migration: notification_log
- [ ] Email templates (welcome, order placed, out for delivery, delivered, refund)
- [ ] Event listeners for the event catalog
- [ ] WebSocket pushes: restaurant dashboard and `/user/queue/notifications` (no order-status tracking)
- [ ] Tests (fake mail sender)

### admin
Restaurant approval (Phase 1), user blocking (Phase 1) and partner approval (Phase 3) are built
with their modules, because earlier phases need them.
- [ ] Admin cancellation from any non-terminal order state
- [ ] Order search

## Phase 5 — Hardening

- [ ] Monitoring and alerting on incomplete event publications; scheduled resubmission
- [ ] Rate limiting for checkout; review the limits added earlier (login, location pings)
- [ ] Caching (restaurant search, menus)
- [ ] Multi-instance STOMP fan-out (Redis pub/sub or a broker relay)
- [ ] Single-run scheduled jobs across instances (e.g. ShedLock) for the payment timeout, assignment and GEO sweeper
- [ ] Observability: metrics, structured logging, dashboards
- [ ] Security review (OWASP checks, dependency scanning, secrets management)
- [ ] Load testing (checkout and tracking)
- [ ] Deployment setup (Docker image, environment config)

## Future

- [ ] `review` module: ratings and reviews for restaurants and delivery partners
- [ ] Push and SMS notifications
- [ ] Maps-provider ETA
