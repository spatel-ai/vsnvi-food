# Security rules

Details: `docs/SECURITY.md`.

- Never commit secrets. In `application*.yaml`, use `${ENV_VAR}` placeholders only. One exception: the `local` profile may give throwaway defaults that match `compose.yaml` (e.g. `${DB_PASSWORD:vsnvi_local_only}`, a local-only JWT key). `dev`, `prod` and every other profile have no default for any secret.
- Never log passwords, tokens, signatures, secrets, full addresses or phone numbers.
- Authenticate every endpoint by default. Add to the public allowlist only what `docs/SECURITY.md` §2 lists.
- Check roles with `@PreAuthorize`. Check ownership in `application` services, against the authenticated user ID and never against a request field.
- When the resource belongs to someone else, return `404`. When the role is wrong, return `403`.
- Hash passwords with BCrypt. Store only the SHA-256 hash of refresh tokens, and rotate them on every use.
- JWTs: issue and validate them with jjwt in our own `JwtAuthenticationFilter` (ADR-002). Don't add `spring-boot-starter-oauth2-resource-server`. Validate signature, `exp` and `iss`. Load the signing key from the environment.
- Verify Razorpay signatures with HMAC-SHA256 over the **raw request body**, compare with `MessageDigest.isEqual`, and do it before parsing.
- For online payments, only a verified webhook may move an order to `PLACED` (cash-on-delivery orders are placed at checkout). The client checkout callback never changes state.
- Deduplicate webhooks by `x-razorpay-event-id`, inserting into `processed_webhooks` in the same transaction as the state change. Make every event consumer idempotent.
- Never call an external system (Razorpay, mail) inside a transaction that holds a row lock.
- Before changing state, re-check the amount, currency and gateway order ID against the stored `Payment`.
- Never store card, UPI or bank details.
- WebSocket: authenticate on STOMP `CONNECT`, and authorize every `SUBSCRIBE` by ownership. Close the session when its JWT expires or the user is blocked.
- Public allowlist patterns are exact paths, never `GET /api/v1/restaurants/**`-style wildcards that would also expose owner-only endpoints.
- The delivery OTP is shown only to the order's customer, never to the partner, and never logged.
- Validate every request record with Bean Validation. Bounds-check coordinates and amounts.
- Error responses must never contain stack traces, SQL or class names.
- Disable Swagger UI and `/v3/api-docs` in prod. In non-prod, allowlist them only through a profile-specific rule. Never put real tokens or secrets in `@Schema` examples.
