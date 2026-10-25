# ADR-002: JWT with the jjwt library and a custom filter

- **Status:** Accepted
- **Date:** 2026-10-25
- **Deciders:** vsnvi-food backend team

## Context

The API is stateless and authenticates requests with short-lived JWT access tokens (see
[SECURITY.md §1](../SECURITY.md#1-authentication-jwt)). The `auth` module both **issues** and
**validates** these tokens, and the same validation is needed on STOMP `CONNECT`. There is no
external identity provider.

Two ways to implement this in Spring Boot:

1. Spring Security's OAuth2 resource server (`spring-boot-starter-oauth2-resource-server`, Nimbus
   JOSE under the hood), plus `JwtEncoder` for issuing.
2. A dedicated JWT library (jjwt) with our own `JwtService` and `JwtAuthenticationFilter`.

## Decision

We use **jjwt** (`io.jsonwebtoken`: `jjwt-api` at compile scope, `jjwt-impl` and one JSON module
at runtime) with a custom filter:

- **`JwtService` (in `auth`):** issues access tokens (`sub`, `role`, `iat`, `exp`, `iss`, `jti`,
  and `kid` in the header) and parses them. HS256 with a key of at least 256 bits from
  `JWT_SIGNING_KEY`. During a key rotation it accepts the old and new keys, chosen by `kid`.
- **`JwtAuthenticationFilter`** (*superseded by [ADR-006](ADR-006-security-filter-chain-in-common.md):
  the filter is `common`'s `BearerTokenAuthenticationFilter`, which calls `auth`'s
  `TokenAuthenticator`*): a `OncePerRequestFilter` registered before
  `UsernamePasswordAuthenticationFilter`. It reads `Authorization: Bearer …`, validates the
  signature, `exp` and `iss` through `JwtService`, and sets the `SecurityContext`. It maps an
  expired token to `TOKEN_EXPIRED` and any other failure to `UNAUTHORIZED`.
- **STOMP:** `auth` implements the `TokenAuthenticator` SPI defined in `common` with the same
  `JwtService`, and the `CONNECT` interceptor in `common` calls it. `common` stays free of any
  dependency on `auth`.
- We do **not** add `spring-boot-starter-oauth2-resource-server`.

**JSON module:** Spring Boot 4 uses Jackson 3 by default, while `jjwt-jackson` is built on
Jackson 2. When adding the dependency, check whether the jjwt version in use supports Jackson 3.
If it doesn't, use `jjwt-gson` (or jjwt's Jackson 2 module, accepting Jackson 2 on the classpath)
and record the choice here.

**Chosen (2026-10-25):** jjwt 0.13.0 ships only a Jackson 2 module (`jjwt-jackson` depends on
`com.fasterxml.jackson.core:jackson-databind`), so we use **`jjwt-gson`** at runtime scope. This
keeps Jackson 2 off the classpath. Revisit if jjwt adds a Jackson 3 module.

## Alternatives considered

### OAuth2 resource server
- **Pros:** maintained by the Spring team; validation, error handling and authority mapping come
  configured; easy switch to RS256/JWKS later.
- **Cons:** it's built around tokens from an external authorization server. Issuing our own tokens
  still needs `JwtEncoder` setup, and mapping our `role` claim and `TOKEN_EXPIRED` error code means
  customising its converters and entry point. It's more framework than we need.
- **Rejected:** the team prefers a small, explicit token service it controls end to end.

## Consequences

- **Positive:** one small, readable `JwtService` used by HTTP and STOMP; full control over claims,
  error codes and key rotation.
- **Negative:** we own the filter's correctness. It needs tests for a missing, malformed, expired,
  wrongly signed and wrong-`iss` token, and for a token signed with `alg: none`, which must be
  rejected.
- **Revisit** if another service needs to verify our tokens: switch to RS256 and publish a JWKS,
  at which point the resource server becomes attractive again.
