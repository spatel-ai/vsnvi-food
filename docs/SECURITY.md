# Security — vsnvi-food

The enforceable rules are in [`.claude/rules/security.md`](../.claude/rules/security.md).
This document explains them.

## 1. Authentication (JWT)

```mermaid
sequenceDiagram
    participant C as Client
    participant A as auth module
    participant DB as PostgreSQL

    C->>A: POST /auth/login {email, password}
    A->>DB: load credential, BCrypt verify
    A->>DB: store hash(refreshToken), familyId, expiresAt
    A-->>C: {accessToken (JWT ~15m), refreshToken (opaque ~30d)}
    C->>A: API calls with Authorization: Bearer accessToken
    C->>A: POST /auth/refresh {refreshToken}
    A->>DB: find by hash; if already used → revoke whole family (reuse detected)
    A->>DB: mark old used, store new token in same family
    A-->>C: new accessToken + new refreshToken
    C->>A: POST /auth/logout
    A->>DB: revoke refresh token family
```

- **Access token:**
  - A JWT that expires after about 15 minutes.
  - Claims: `sub` (the user ID), `role`, `iat`, `exp`, `iss` and `jti`.
  - Signed with HS256, using a key of at least 256 bits from the environment. We may switch to
    RS256 when another service needs to verify tokens.
  - Issued and validated with the **jjwt** library ([ADR-002](decisions/ADR-002-jwt-with-jjwt.md)),
    not Spring's OAuth2 resource server. A `JwtService` in `auth` issues and parses tokens.
  - `common`'s `BearerTokenAuthenticationFilter` (`OncePerRequestFilter`, registered before
    `UsernamePasswordAuthenticationFilter`) passes the token to the `TokenAuthenticator` SPI, which
    `auth` implements with `JwtService`. It validates the signature, `exp` and `iss` statelessly,
    and puts the user ID and role into the `SecurityContext` ([ADR-006](decisions/ADR-006-security-filter-chain-in-common.md)). An expired token produces
    `401 TOKEN_EXPIRED`; any other invalid token produces `401 UNAUTHORIZED`.
  - The same `JwtService` validates the token on STOMP `CONNECT`: `auth` implements the
    `TokenAuthenticator` SPI that the STOMP interceptor in `common` calls (see §5).
- **Refresh token:**
  - A random value (at least 256 bits) that expires after about 30 days.
  - Only its **SHA-256 hash** is stored.
  - It is **rotated on every use**. Reusing a token that has already been rotated revokes the
    whole token family.
- **Logout** revokes the family. Access tokens are short-lived, so we don't keep a denylist.
- **Blocked users:** when an admin blocks a user, all of that user's refresh tokens are revoked.
  Their remaining access tokens stop working within 15 minutes.
- **Passwords:**
  - Hashed with BCrypt (strength 12) via `DelegatingPasswordEncoder`.
  - Minimum length is 8.
  - Login is rate-limited per IP and per account (`auth.login-rate-limit.*`, Redis
    `ratelimit:login:*`), and registration and refresh per IP:
    - every login attempt counts against the client IP (30 per 5 minutes);
    - failed attempts count against the account (10 per 15 minutes), and a successful login resets
      the count. Unknown emails are counted the same way, so the limit doesn't reveal which accounts
      exist. The key holds a SHA-256 of the email, never the email;
    - registration counts against the client IP (20 per hour, `auth.register-rate-limit.*`,
      Redis `ratelimit:register:*`), which bounds email probing through
      `EMAIL_ALREADY_REGISTERED` and the BCrypt cost of each registration;
    - refresh counts against the client IP (120 per 5 minutes, `auth.refresh-rate-limit.*`, Redis
      `ratelimit:refresh:*`);
    - over a limit returns `429 RATE_LIMITED` with `Retry-After`;
    - if Redis is unavailable the limiter allows the request and logs a warning (fails open);
    - in dev and prod, `server.forward-headers-strategy: native` takes the client IP from
      `X-Forwarded-For` only when the request comes from an internal proxy.
- **Login errors** are generic ("invalid credentials"). They never reveal whether an email exists.
- **Password length:** BCrypt accepts at most 72 *bytes*, so registration also checks the UTF-8
  byte length (`@MaxUtf8Bytes(72)`), and login answers an over-long password with
  `INVALID_CREDENTIALS` instead of reaching BCrypt.
- **Algorithm pinning:** only `HS256` access tokens are accepted, even if a longer key is
  configured later; `alg: none` is always rejected.
- **Credential endpoints ignore the bearer header:** `POST /auth/register`, `/login` and `/refresh`
  never read `Authorization`, so a client that still sends its expired access token can refresh.

## 2. Roles and authorization

| Role               | Can                                                                    |
|--------------------|------------------------------------------------------------------------|
| `CUSTOMER`         | Browse, manage their own cart, addresses and orders, pay, track their own orders |
| `RESTAURANT_OWNER` | Manage their **own** restaurants, menus, coupons, and their restaurants' orders |
| `RIDER`            | Set availability, ping location, act on tasks **assigned to them**     |
| `ADMIN`            | Manage platform coupons, approve or suspend restaurants and delivery partners, block users, search orders, cancel any non-terminal order, issue manual refunds |

- **Default deny:** every endpoint requires authentication unless it is on the public allowlist.
  The allowlist uses **exact patterns** (all under `/api/v1`). A wildcard such as
  `GET /restaurants/**` is not allowed, because it would also make owner-only endpoints such as
  `GET /restaurants/me`, `/restaurants/{id}/orders` and `/restaurants/{id}/coupons` public.
  - `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`;
  - `GET /restaurants`, `GET /restaurants/{id}`, `GET /restaurants/{id}/menu`,
    `GET /menu-items/{id}`;
  - `POST /payments/webhooks/razorpay`;
  - `/actuator/health`;
  - **non-prod only:** `/v3/api-docs/**`, `/swagger-ui.html` and `/swagger-ui/**`.
- **Two layers of checks:**
  - Role checks: `@PreAuthorize` on controllers or services.
  - Ownership checks: done **in the application services** (`application/`), against the authenticated user ID,
    and never against an ID taken from the request body.
- **Ownership failures return `404`**, so the API doesn't reveal that the resource exists.
  Role failures return `403`.
- **Clients never choose privileged roles.** Registration can't create an `ADMIN`.
- **Admins can't be blocked** (`422 CANNOT_BLOCK_ADMIN`), so the platform can't lose its last admin.
- **First admin:** on startup, an `AdminBootstrap` runner in `auth` creates an `ADMIN` from
  `ADMIN_BOOTSTRAP_EMAIL` and `ADMIN_BOOTSTRAP_PASSWORD` **only if no admin exists yet**. It does
  nothing when those variables are unset. Further admins are created by an existing admin
  (a later endpoint), never by registration.
- **Admin approval is required for:**
  - **Restaurants:** a new restaurant starts `PENDING_APPROVAL`. It is hidden from search and
    can't receive orders until an admin approves it.
  - **Delivery partners:** a new partner profile starts `PENDING_APPROVAL`. The partner can't go
    online or receive offers until an admin approves them.
  - Owner and customer *accounts* themselves need no approval.
  - Admins can later suspend restaurants and partners.

## 3. Payment security (Razorpay)

- **Card data:** we never receive or store card, UPI or bank details. Razorpay Checkout handles
  them, which keeps us out of most PCI scope.
- **Webhook signature:**
  - Verify `X-Razorpay-Signature` as `HMAC_SHA256(rawBody, webhookSecret)` over the **raw request
    bytes**, before any JSON parsing.
  - Compare with `MessageDigest.isEqual`.
  - Reject anything invalid with `400` and log it without the payload.
- **Checkout signature:** verify `HMAC_SHA256(orderId|paymentId, keySecret)` the same way. It only
  updates the UI and never changes order state.
- **Cross-checks:** before changing state, re-check the amount, currency and `razorpay_order_id`
  against the stored `Payment`.
- **Idempotency:**
  - Webhooks are deduplicated by `x-razorpay-event-id`, using a unique constraint on
    `processed_webhooks`, inserted in the same transaction as the state change.
  - State changes are monotonic.
- **Webhook endpoint:**
  - Public, but authenticated by signature only.
  - Optionally also restricted to Razorpay's published IP ranges at the edge.
  - CSRF doesn't apply because the API is stateless.
- **Refunds** happen in exactly three ways, and every refund is audited:
  - `order` calls `PaymentApi.cancelPayment` when an order is rejected or cancelled after capture;
  - `payment` itself refunds a capture that arrives after the payment was voided (a late webhook);
  - an `ADMIN` calls `POST /payments/{id}/refund`.
- **Secrets:** the key secret and the webhook secret are separate secrets, and both are rotated
  through Razorpay's dashboard.

## 4. Secrets handling

- **Where secrets live:** environment variables, or a secret manager (AWS Secrets Manager, Vault
  or similar) in deployed environments. They are referenced in `application.yaml` as
  `${RAZORPAY_KEY_SECRET}` and similar.
- **Never in git:**
  - `application*.yaml` contains no secret values, only placeholders. **Exception:** the `local`
    profile (the default when no profile is active) has throwaway defaults that match
    `compose.yaml`, so a fresh clone runs with no setup. They're useless outside a developer's
    machine, and `LocalProfileGuard` refuses to start `local` against a non-local database. `dev` and `prod` have no defaults for secrets, and deployments must set
    `SPRING_PROFILES_ACTIVE` explicitly.
  - `.env` and `application-local.yaml` are git-ignored. `.env.example` is committed with
    local-only values and no real credentials.
  - The `local` profile optionally imports `.env` (`optional:file:.env[.properties]`), which
    `compose.yaml` also reads, to override those defaults. `dev` and `prod` take every secret from the real environment, with
    no defaults.
- **Inventory:**
  - `JWT_SIGNING_KEY`
  - `DB_PASSWORD`
  - `REDIS_PASSWORD`
  - `RAZORPAY_KEY_ID`
  - `RAZORPAY_KEY_SECRET`
  - `RAZORPAY_WEBHOOK_SECRET`
  - `MAIL_PASSWORD`
  - `ADMIN_BOOTSTRAP_PASSWORD` (with `ADMIN_BOOTSTRAP_EMAIL`; remove both after the first admin exists)
- **Never log:** passwords, tokens, signatures, secrets, full addresses or phone numbers. Mask
  them when they need to appear at all.
- **Rotation:** rotate secrets whenever they might be exposed. The JWT key should support a
  `kid` claim, so two keys can be valid during a rotation.

## 5. WebSocket (STOMP)

- **CONNECT:** the `ChannelInterceptor` in `common` passes the bearer token from the STOMP
  `CONNECT` frame to the `TokenAuthenticator` SPI, which `auth` implements with `JwtService`, and
  sets the session principal. Connections without a valid token are rejected.
- **Session lifetime:** a STOMP session must not outlive its token.
  - `StompSessionRegistry` in `common` stores each session's token `exp`, and a scheduled sweep
    closes sessions whose `exp` has passed. The client reconnects with a fresh token.
  - When a user is blocked, `auth` (which listens to `UserBlocked`) revokes their refresh tokens
    and calls `StompSessionRegistry.closeSessionsOf(userId)`.
- **SUBSCRIBE:** every subscription is authorized.
  - `/topic/orders/{id}/tracking`: only the order's customer, or an admin.
  - `/topic/restaurants/{id}/orders`: only that restaurant's owner.
  - `/user/queue/**`: the principal's own queue only.
- **Who authorizes subscriptions:** the destination's owning module, through the
  `SubscriptionAuthorizer` SPI in `common` (see [ARCHITECTURE.md](ARCHITECTURE.md#6-real-time-tracking-redis-geo--websocket-stomp)).
- **SEND:** to `/app/delivery/location`, only allowed for `RIDER`, and rate-limited.
- **Delivery OTP:** generated with `SecureRandom`, shown only to the order's customer (never to the
  partner), compared in constant time, limited to 5 attempts per task, cleared when the task
  ends, and never logged. It isn't hashed, because the customer must be able to see it (see
  [ARCHITECTURE.md §6](ARCHITECTURE.md#6-real-time-tracking-redis-geo--websocket-stomp)).
- **Origins:** allowed origins are configured explicitly. Never use `*` in production.

## 6. Other controls

- **Input validation:** Bean Validation on every request record. Coordinates and amounts are
  bounds-checked.
- **Rate limiting:**
  - Applies to auth endpoints, location pings and checkout.
  - Exceeding the limit returns `429` with `RATE_LIMITED`.
- **CORS:** an explicit allowlist per environment.
- **Transport:** HTTPS only, terminated at the edge, with HSTS.
- **API docs (springdoc):**
  - Swagger UI and `/v3/api-docs` are **disabled in prod**
    (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`), so the attack
    surface isn't advertised.
  - In non-prod they are on the allowlist as a profile-specific `SecurityFilterChain` rule, never
    with a blanket `permitAll`.
  - Swagger UI's "Authorize" sends the access token from the browser only. Never put real tokens
    or secrets in `@Schema(example = …)`.
- **Errors:** error responses never expose stack traces or internals (see
  [API-DESIGN.md](API-DESIGN.md#4-error-format)).
- **Dependencies:** dependency scanning and an OWASP review are part of Phase 5 in
  [PROGRESS.md](PROGRESS.md).
