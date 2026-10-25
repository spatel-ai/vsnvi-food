# API Design — vsnvi-food

## 1. URL rules

- **Prefix:** every REST endpoint starts with **`/api/v1`**.
- **Resource names:** plural, kebab-case nouns, e.g. `/restaurants`, `/menu-items`,
  `/delivery-tasks`.
- **Nesting:** at most **two levels**, e.g. `/restaurants/{restaurantId}/menu-items`.
  Go deeper only through a top-level resource, e.g. `/menu-items/{id}`.
- **Path variables:** always UUIDs.
- **Actions:** a state change that isn't plain CRUD is a `POST` to an action sub-resource,
  e.g. `POST /orders/{id}/cancel` or `POST /orders/{id}/accept`.
- **Current user:** resources belonging to the caller use `/me`, e.g. `GET /users/me` or
  `GET /users/me/orders`.
- **Query parameters:** camelCase, e.g. `?cuisine=north-indian&minRating=4&page=0&size=20`.
- **Not allowed:** verbs in paths (other than action sub-resources), file extensions and
  trailing slashes.
- **WebSocket:** the STOMP endpoint is `/ws`. Destinations are listed in [§10](#10-websocket-stomp-destinations).

## 2. Request and response format

- **Content type:** JSON only (`application/json`).
- **Field names:** camelCase.
- **Timestamps:** ISO-8601 in UTC, e.g. `"2026-10-25T14:03:00Z"`.
- **IDs:** UUID strings.
- **Money:** always an object with a string amount, so no precision is lost:
  `{ "amount": "249.00", "currency": "INR" }`.
- **Enums:** UPPER_SNAKE_CASE strings, e.g. `"READY_FOR_PICKUP"`.
- **Nulls:** null fields are omitted from responses.
- **Response body:** a single resource is returned directly. Lists use the pagination envelope in [§5](#5-pagination-sorting-and-filtering).

## 3. HTTP methods and status codes

| Method   | Use                  | Success code                                           |
|----------|----------------------|--------------------------------------------------------|
| `GET`    | Read                 | `200 OK`                                               |
| `POST`   | Create / action      | `201 Created` + `Location` for creation; `200 OK` for actions |
| `PUT`    | Full replace         | `200 OK`                                               |
| `PATCH`  | Partial update       | `200 OK`                                               |
| `DELETE` | Delete               | `204 No Content`                                       |

| Error | When                                                                                     |
|-------|------------------------------------------------------------------------------------------|
| `400` | The request is malformed or fails validation                                             |
| `401` | The token is missing, invalid or expired                                                 |
| `403` | The caller is authenticated but their **role** doesn't allow the action                  |
| `404` | The resource doesn't exist, **or it belongs to someone else** (ownership failures are hidden as 404) |
| `405` | The HTTP method isn't supported on this path                                             |
| `406` | The `Accept` header allows no media type we can produce                                  |
| `409` | Invalid state transition, duplicate, optimistic-lock failure, or an idempotency key reused with a different body |
| `413` | The request body or upload is too large                                                  |
| `415` | The request `Content-Type` isn't supported                                               |
| `422` | A business rule is violated (restaurant closed, item unavailable, outside the delivery area) |
| `429` | Rate limited                                                                             |
| `500` | Unexpected server error                                                                  |

## 4. Error format

Every error response uses **RFC 9457 Problem Details** (Spring `ProblemDetail`) with these
extension fields:

- `code`
- `traceId`
- `timestamp`
- `errors`, for validation failures only

```json
{
  "type": "https://api.vsnvi.com/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request has 2 invalid fields",
  "instance": "/api/v1/orders",
  "code": "VALIDATION_FAILED",
  "traceId": "a1b2c3d4e5f6",
  "timestamp": "2026-10-25T14:03:00Z",
  "errors": [
    { "field": "items[0].quantity", "message": "must be greater than 0" },
    { "field": "addressId", "message": "must not be null" }
  ]
}
```

- Use `Content-Type: application/problem+json`.
- `type` is `https://api.vsnvi.com/errors/<code-in-kebab-case>`.
- Never include stack traces, SQL or class names in an error response.

### Error code catalog (initial)

Any framework error whose status has no code below is returned as `MALFORMED_REQUEST` (400) or
`INTERNAL_ERROR` (500), so `status`, `code` and `type` always agree.

| Code                        | HTTP | Meaning                                    |
|-----------------------------|------|--------------------------------------------|
| `VALIDATION_FAILED`         | 400  | Bean validation errors                     |
| `MALFORMED_REQUEST`         | 400  | Unreadable JSON or wrong types             |
| `INVALID_WEBHOOK_SIGNATURE` | 400  | Razorpay signature check failed            |
| `UNAUTHORIZED`              | 401  | Missing or invalid token                   |
| `TOKEN_EXPIRED`             | 401  | Access token expired; refresh it           |
| `INVALID_CREDENTIALS`       | 401  | Wrong email or password (never says which) |
| `FORBIDDEN`                 | 403  | Role not allowed                           |
| `ACCOUNT_BLOCKED`           | 403  | User is blocked by an admin                |
| `RESOURCE_NOT_FOUND`        | 404  | Not found, or not yours                    |
| `METHOD_NOT_ALLOWED`        | 405  | HTTP method not supported on this path     |
| `NOT_ACCEPTABLE`            | 406  | No acceptable response media type          |
| `EMAIL_ALREADY_REGISTERED`  | 409  | Duplicate registration                     |
| `INVALID_ORDER_STATE`       | 409  | Illegal order status transition            |
| `CONCURRENT_MODIFICATION`   | 409  | Optimistic lock failure; retry             |
| `IDEMPOTENCY_KEY_CONFLICT`  | 409  | Same key, different payload                |
| `PAYLOAD_TOO_LARGE`         | 413  | Request body or upload too large           |
| `UNSUPPORTED_MEDIA_TYPE`    | 415  | Request `Content-Type` not supported       |
| `CART_RESTAURANT_MISMATCH`  | 422  | Item from a different restaurant           |
| `COUPON_INVALID`            | 422  | Unknown, inactive or expired coupon        |
| `COUPON_NOT_APPLICABLE`     | 422  | Minimum subtotal, restaurant or first-order rule not met |
| `COUPON_LIMIT_REACHED`      | 422  | Per-customer or total usage limit reached  |
| `PRICE_CHANGED`             | 422  | Checkout price differs from the cart preview; re-read the cart |
| `ITEM_UNAVAILABLE`          | 422  | Menu item out of stock or disabled         |
| `RESTAURANT_CLOSED`         | 422  | Outside opening hours                      |
| `OUT_OF_DELIVERY_AREA`      | 422  | Address outside serviceable radius         |
| `PAYMENT_FAILED`            | 422  | Payment declined or failed                 |
| `INVALID_DELIVERY_OTP`      | 422  | Wrong delivery OTP                         |
| `PARTNER_NOT_APPROVED`      | 422  | Partner can't go online before approval    |
| `RATE_LIMITED`              | 429  | Too many requests                          |
| `INTERNAL_ERROR`            | 500  | Unexpected error                           |

## 5. Pagination, sorting and filtering

**Offset pagination** is the default for most listings.

- Request: `?page=0&size=20&sort=createdAt,desc`.
- Defaults are `page=0` and `size=20`. The maximum `size` is `100`.
- Response:

```json
{
  "content": [ { "...": "..." } ],
  "page": { "number": 0, "size": 20, "totalElements": 134, "totalPages": 7 }
}
```

**Cursor pagination** is for fast-growing feeds, such as order history and delivery task history.

- Request: `?limit=20&cursor=<opaque>`.
- Response: `{ "content": [...], "nextCursor": "…" }`. `nextCursor` is absent on the last page.

**Filtering** uses plain query parameters, e.g. `?status=DELIVERED&from=2026-10-01T00:00:00Z`.
Only documented filter and sort fields are accepted. Anything else returns `400`.

## 6. Idempotency

- **Where it's required:** an `Idempotency-Key: <uuid>` header on `POST /orders` and
  `POST /payments/{id}/retry`.
- **Storage:** the server keeps the key, a hash of the request and the response for 24 hours,
  scoped to the caller.
  - The same key with the same body returns the stored response.
  - The same key with a different body returns `409 IDEMPOTENCY_KEY_CONFLICT`.
- **Webhooks:** Razorpay webhooks don't use this header. They are deduplicated by
  `x-razorpay-event-id`, as described in [ARCHITECTURE.md](ARCHITECTURE.md#7-payment-flow-razorpay).

## 7. Authentication

- **Access token:** sent as `Authorization: Bearer <accessToken>`.
- **Refresh:** `POST /api/v1/auth/refresh`.
- The full flow is in [SECURITY.md](SECURITY.md).

## 8. Versioning

- **Location:** the major version is in the URL (`/api/v1`).
- **Additive changes** (new fields or endpoints) don't need a new version. Clients must ignore
  fields they don't recognise.
- **Breaking changes** go into `/api/v2`.
  - `v1` remains supported for at least one release cycle.
  - While deprecated, `v1` responses carry `Deprecation` and `Sunset` headers.
- **WebSocket message payloads** follow the same additive-only rule.

## 9. Initial endpoint list

Roles: **C** = CUSTOMER, **O** = RESTAURANT_OWNER, **R** = RIDER, **A** = ADMIN,
**Public** = no authentication.

### auth

| Method | Path                     | Role   | Description                                 |
|--------|--------------------------|--------|---------------------------------------------|
| POST   | `/api/v1/auth/register`  | Public | Register a `CUSTOMER`, `RESTAURANT_OWNER` or `RIDER` |
| POST   | `/api/v1/auth/login`     | Public | Get access and refresh tokens               |
| POST   | `/api/v1/auth/refresh`   | Public | Rotate tokens                               |
| POST   | `/api/v1/auth/logout`    | Any    | Revoke the refresh token                    |

### user

| Method | Path                              | Role | Description            |
|--------|-----------------------------------|------|------------------------|
| GET    | `/api/v1/users/me`                | Any  | Current profile        |
| PATCH  | `/api/v1/users/me`                | Any  | Update profile         |
| GET    | `/api/v1/users/me/addresses`      | C    | List addresses         |
| POST   | `/api/v1/users/me/addresses`      | C    | Add an address         |
| PUT    | `/api/v1/users/me/addresses/{id}` | C    | Update an address      |
| DELETE | `/api/v1/users/me/addresses/{id}` | C    | Delete an address      |

### restaurant

| Method | Path                                     | Role   | Description                                       |
|--------|------------------------------------------|--------|---------------------------------------------------|
| GET    | `/api/v1/restaurants`                    | Public | Search by `lat`, `lng`, `cuisine`, `minRating`, `isOpen` and `q` (paginated) |
| GET    | `/api/v1/restaurants/{id}`               | Public | Restaurant details                                |
| POST   | `/api/v1/restaurants`                    | O      | Create a restaurant (starts pending approval)     |
| PATCH  | `/api/v1/restaurants/{id}`               | O      | Update your own restaurant, including `deliveryFee` and `packagingCharge` |
| PUT    | `/api/v1/restaurants/{id}/opening-hours` | O      | Set opening hours                                 |
| POST   | `/api/v1/restaurants/{id}/toggle-open`   | O      | Temporarily open or close                         |
| GET    | `/api/v1/restaurants/me`                 | O      | List your own restaurants                         |

### menu

| Method | Path                                       | Role   | Description                      |
|--------|--------------------------------------------|--------|----------------------------------|
| GET    | `/api/v1/restaurants/{id}/menu`            | Public | Full menu, grouped by category   |
| POST   | `/api/v1/restaurants/{id}/menu-categories` | O      | Create a category                |
| PATCH  | `/api/v1/menu-categories/{id}`             | O      | Update a category                |
| DELETE | `/api/v1/menu-categories/{id}`             | O      | Delete a category                |
| POST   | `/api/v1/restaurants/{id}/menu-items`      | O      | Create an item (with variants and add-ons) |
| GET    | `/api/v1/menu-items/{id}`                  | Public | Item details                     |
| PATCH  | `/api/v1/menu-items/{id}`                  | O      | Update an item                   |
| POST   | `/api/v1/menu-items/{id}/availability`     | O      | Mark in stock or out of stock    |
| DELETE | `/api/v1/menu-items/{id}`                  | O      | Delete an item                   |

### cart

| Method | Path                          | Role | Description                                        |
|--------|-------------------------------|------|----------------------------------------------------|
| GET    | `/api/v1/cart`                | C    | Current cart with computed totals                  |
| POST   | `/api/v1/cart/items`          | C    | Add an item (`422` if it's from a different restaurant) |
| PATCH  | `/api/v1/cart/items/{itemId}` | C    | Change quantity or options                         |
| DELETE | `/api/v1/cart/items/{itemId}` | C    | Remove an item                                     |
| DELETE | `/api/v1/cart`                | C    | Clear the cart                                     |
| POST   | `/api/v1/cart/coupon`         | C    | Apply a coupon code (preview only; one coupon per cart) |
| DELETE | `/api/v1/cart/coupon`         | C    | Remove the coupon                                  |

`GET /api/v1/cart` returns the full price breakdown: item subtotal, packaging charge, delivery
fee, coupon discount, tax and total.

### promotion

| Method | Path                                      | Role   | Description                                       |
|--------|-------------------------------------------|--------|---------------------------------------------------|
| GET    | `/api/v1/coupons/available?restaurantId=` | C      | Coupons the customer can currently use there      |
| POST   | `/api/v1/restaurants/{id}/coupons`        | O      | Create a restaurant coupon                        |
| GET    | `/api/v1/restaurants/{id}/coupons`        | O      | List your restaurant's coupons                    |
| PATCH  | `/api/v1/coupons/{id}`                    | O/A    | Update a coupon (owner of its restaurant, or admin) |
| POST   | `/api/v1/coupons/{id}/deactivate`         | O/A    | Deactivate a coupon                               |
| POST   | `/api/v1/admin/coupons`                   | A      | Create a platform coupon                          |
| GET    | `/api/v1/admin/coupons`                   | A      | List all coupons                                  |

### order

| Method | Path                              | Role    | Description                                    |
|--------|-----------------------------------|---------|------------------------------------------------|
| POST   | `/api/v1/orders`                  | C       | Check out the cart (`Idempotency-Key`). Returns the order and Razorpay details. If the Razorpay order couldn't be created, `payment.razorpayOrderId` is absent and the client calls `/payments/{id}/retry` |
| GET    | `/api/v1/orders/{id}`             | C/O/R/A | Order details (ownership enforced)             |
| GET    | `/api/v1/users/me/orders`         | C       | Order history (cursor pagination)              |
| POST   | `/api/v1/orders/{id}/cancel`      | C/A     | Cancel. Customers only while `PLACED`; admins from any non-terminal state |
| GET    | `/api/v1/restaurants/{id}/orders` | O       | Incoming or active orders (`status` filter)    |
| POST   | `/api/v1/orders/{id}/accept`      | O       | Accept, with `prepTimeMinutes`                 |
| POST   | `/api/v1/orders/{id}/reject`      | O       | Reject, with a reason                          |
| POST   | `/api/v1/orders/{id}/preparing`   | O       | Mark as preparing                              |
| POST   | `/api/v1/orders/{id}/ready`       | O       | Mark as ready for pickup                       |

### payment

| Method | Path                                 | Role   | Description                                              |
|--------|--------------------------------------|--------|----------------------------------------------------------|
| GET    | `/api/v1/payments/{id}`              | C/A    | Payment status                                           |
| POST   | `/api/v1/payments/{id}/verify`       | C      | Verify the checkout signature (UX hint only)             |
| POST   | `/api/v1/payments/{id}/retry`        | C      | Open a Razorpay order for a pending payment, e.g. when checkout couldn't reach Razorpay (`Idempotency-Key`) |
| POST   | `/api/v1/payments/webhooks/razorpay` | Public | Razorpay webhook (signature verified)                    |
| POST   | `/api/v1/payments/{id}/refund`       | A      | Manual refund                                            |

### delivery

| Method | Path                                  | Role | Description                                |
|--------|---------------------------------------|------|--------------------------------------------|
| POST   | `/api/v1/delivery/availability`       | R    | Go online or offline (`422 PARTNER_NOT_APPROVED` until approved) |
| POST   | `/api/v1/delivery/location`           | R    | Location ping (`lat`, `lng`, `heading`, `speed`) |
| GET    | `/api/v1/delivery-tasks/offers`       | R    | Pending task offers                        |
| POST   | `/api/v1/delivery-tasks/{id}/accept`  | R    | Accept an offer                            |
| POST   | `/api/v1/delivery-tasks/{id}/decline` | R    | Decline an offer                           |
| POST   | `/api/v1/delivery-tasks/{id}/pickup`  | R    | Confirm pickup. Calls `OrderApi.markPickedUp`, and `order` publishes `OrderPickedUp` |
| POST   | `/api/v1/delivery-tasks/{id}/deliver` | R    | Confirm delivery with OTP. Calls `OrderApi.markDelivered`, and `order` publishes `OrderDelivered` |
| GET    | `/api/v1/delivery-tasks/me`           | R    | Active and past tasks (cursor pagination)  |
| GET    | `/api/v1/orders/{id}/tracking`        | C    | Tracking snapshot: status, latest location, ETA and the delivery OTP. Owned by `delivery`. Before `READY_FOR_PICKUP` it returns only the order status (read through `OrderApi`) |

### admin

Admin endpoints live in the module that owns the data, e.g. `RestaurantAdminController` in
`restaurant`. They share the `/api/v1/admin` prefix and the `admin` OpenAPI group.

| Method | Path                                     | Role | Description                         |
|--------|------------------------------------------|------|-------------------------------------|
| GET    | `/api/v1/admin/restaurants`              | A    | List restaurants (`status` filter)  |
| POST   | `/api/v1/admin/restaurants/{id}/approve` | A    | Approve a restaurant                |
| POST   | `/api/v1/admin/restaurants/{id}/suspend` | A    | Suspend a restaurant                |
| GET    | `/api/v1/admin/users`                    | A    | List users (`role`, `status` filters; sort by `createdAt`, `email`, `fullName`) |
| POST   | `/api/v1/admin/users/{id}/block`         | A    | Block a user                        |
| GET    | `/api/v1/admin/delivery-partners`        | A    | List partners (`status` filter)     |
| POST   | `/api/v1/admin/delivery-partners/{id}/approve` | A | Approve a partner              |
| POST   | `/api/v1/admin/delivery-partners/{id}/suspend` | A | Suspend a partner              |
| GET    | `/api/v1/admin/orders`                   | A    | Search all orders                   |

### operations

| Method | Path                | Role   | Description  |
|--------|---------------------|--------|--------------|
| GET    | `/actuator/health`  | Public | Health check |
| GET    | `/actuator/info`    | A      | Build info   |
| GET    | `/actuator/metrics` | A      | Metrics      |
| GET    | `/v3/api-docs`, `/v3/api-docs/{group}` | Public (non-prod) | OpenAPI spec (JSON); append `.yaml` for YAML |
| GET    | `/swagger-ui.html`  | Public (non-prod) | Swagger UI   |

## 10. WebSocket (STOMP) destinations

The client connects to `/ws` and sends `Authorization: Bearer <accessToken>` in the STOMP
`CONNECT` headers.

| Destination                                | Direction        | Role | Payload / purpose                              |
|--------------------------------------------|------------------|------|------------------------------------------------|
| `/topic/orders/{orderId}/tracking`         | server → client  | C    | Published by `delivery` from `READY_FOR_PICKUP` onwards: `status` (READY_FOR_PICKUP, PARTNER_ASSIGNED, PICKED_UP, DELIVERED, CANCELLED), `location` {lat, lng, heading}, `eta` {etaMinutes}. Earlier statuses: poll `GET /orders/{id}` |
| `/topic/restaurants/{restaurantId}/orders` | server → client  | O    | New and updated orders for the dashboard       |
| `/user/queue/offers`                       | server → client  | R    | Delivery task offers                           |
| `/user/queue/notifications`                | server → client  | Any  | In-app notifications from `notification` (e.g. refund issued); not order-status tracking |
| `/app/delivery/location`                   | client → server  | R    | Optional alternative to the REST location ping |

Every message is a JSON envelope: `{ "type": "status|location|eta|offer|…", "occurredAt": "…", "data": { … } }`.

## 11. OpenAPI and Swagger UI

The API contract is **generated from code** with springdoc-openapi 3.x (OpenAPI 3). We don't
maintain a hand-written spec. Annotations on the controllers and records are what keep the docs
accurate.

### Endpoints

| Path                       | Purpose                                                  |
|----------------------------|----------------------------------------------------------|
| `/v3/api-docs`             | Full spec (JSON). `/v3/api-docs.yaml` returns YAML        |
| `/v3/api-docs/{group}`     | Spec for one module group                                 |
| `/swagger-ui.html`         | Swagger UI, which redirects to `/swagger-ui/index.html`   |

### Availability per environment

| Profile        | `springdoc.api-docs.enabled` | `springdoc.swagger-ui.enabled` | Access                 |
|----------------|------------------------------|--------------------------------|------------------------|
| `local`, `dev` | `true`                       | `true`                         | Public (no token)      |
| `test`         | `true`                       | `false`                        | Used by contract tests |
| `prod`         | `false`                      | `false`                        | Not exposed            |

If prod ever needs the spec, expose `/v3/api-docs` to `ADMIN` only and keep Swagger UI off.

### Global configuration (one `OpenApiConfig` in `common`)

- **Info:**
  - Title `vsnvi-food API`.
  - Version taken from the build (`info.app.version`).
  - Contact and description.
- **Servers:** one entry per environment, from config. No hard-coded hosts.
- **Security scheme:**
  - `bearerAuth`: `type: http`, `scheme: bearer`, `bearerFormat: JWT`.
  - Applied globally, so Swagger UI's "Authorize" button sends the access token.
  - Public endpoints override it with `@SecurityRequirements()` (an empty list).
- **Groups:** one `GroupedOpenApi` per module, matched by path:

| Group          | Paths                                                         |
|----------------|---------------------------------------------------------------|
| `auth`         | `/api/v1/auth/**`                                             |
| `user`         | `/api/v1/users/**`                                            |
| `restaurant`   | `/api/v1/restaurants`, `/api/v1/restaurants/*`, `/api/v1/restaurants/*/opening-hours`, `/api/v1/restaurants/*/toggle-open` |
| `menu`         | `/api/v1/restaurants/*/menu`, `/api/v1/restaurants/*/menu-*`, `/api/v1/menu-items/**`, `/api/v1/menu-categories/**` |
| `cart`         | `/api/v1/cart/**`                                             |
| `promotion`    | `/api/v1/coupons/**`, `/api/v1/restaurants/*/coupons`         |
| `order`        | `/api/v1/orders/**` (excluding `/api/v1/orders/*/tracking`), `/api/v1/users/me/orders`, `/api/v1/restaurants/*/orders` |
| `payment`      | `/api/v1/payments/**`                                         |
| `delivery`     | `/api/v1/delivery/**`, `/api/v1/delivery-tasks/**`, `/api/v1/orders/*/tracking` |
| `admin`        | `/api/v1/admin/**` (cross-module group, matched by path)      |

  These patterns are Ant-style: `*` matches one path segment. Some paths are nested under another
  module's resource, such as `/users/me/orders`, `/restaurants/{id}/menu` or
  `/orders/{id}/tracking`. Each one is listed under the module that **owns the controller**, and
  excluded from the other group with `pathsToExclude`.
- **Error schema:**
  - A shared `ProblemDetail` schema (including `code`, `traceId`, `timestamp` and `errors[]`) is
    registered once as a component.
  - An `OpenApiCustomizer` adds the default responses below to every operation, so controllers
    don't repeat them:
    - `400`, `401`, `403`, `404`, `429` and `500` → `application/problem+json`;
    - `409` and `422` on mutating operations.
- **Idempotency header:** operations that require `Idempotency-Key` declare it as a required
  header parameter, using a reusable `@IdempotencyKeyHeader` meta-annotation.
- **Pagination:** document `page`, `size` and `sort` with `@ParameterObject Pageable`, and cursor
  endpoints with explicit `limit` and `cursor` parameters. Response envelopes are generic records
  (`PageResponse<T>` and `CursorPageResponse<T>`).
- **Money:** documented as an object whose `amount` is a string with the pattern
  `^-?\d+\.\d{2}$`, plus `currency` (ISO 4217).

### Annotation conventions (`api/web/`)

- **Class:** `@Tag(name = "<module>", description = "…")`. The tag is the module that owns the
  controller, so it usually matches the group name. Admin controllers are the exception: they
  keep their module's tag but appear in the `admin` group.
- **Every handler:**
  - `@Operation(summary = "…", operationId = "<verbResource>")`, e.g. `operationId = "placeOrder"`.
  - `operationId` must be unique across the whole API, because client SDKs are generated from it.
- **Responses:** declare `@ApiResponse` only for **success** responses and for any error that is
  specific to that operation (with an example `code`). Common errors come from the customizer.
- **Records:**
  - Use `@Schema(description, example)` on fields that aren't self-explanatory.
  - Bean Validation annotations (`@NotBlank`, `@Size`, `@Positive` and so on) appear in the
    schema automatically, so don't repeat them in `@Schema`.
- **Enums:** document them as strings; springdoc lists the values automatically.
- **Hidden endpoints:** the Razorpay webhook (`/payments/webhooks/razorpay`) is documented, with
  a note that it is for Razorpay only and authenticated by signature. Internal or test-only
  endpoints get `@Hidden`.
- **Entities:** never expose a JPA entity in a schema. Only request and response records appear.

### WebSocket documentation

OpenAPI doesn't describe STOMP. The destinations and payloads in [§10](#10-websocket-stomp-destinations)
are the contract, and the message payload records are listed in the spec as plain schemas under a
`realtime` tag, through an `OpenApiCustomizer`.

### Spec in CI

- An integration test fetches `/v3/api-docs` and saves it as `target/openapi.json`, which is
  published as a build artifact.
- Breaking changes (removed paths or fields, new required fields) must go in `/api/v2`, as
  [§8](#8-versioning) explains.
- A spec-diff check, e.g. `openapi-diff`, can enforce this later.
