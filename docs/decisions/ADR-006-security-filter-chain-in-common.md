# ADR-006: Security filter chain in `common`, token validation behind an SPI

- **Status:** Accepted
- **Date:** 2026-10-25
- **Deciders:** vsnvi-food backend team

## Context

ADR-002 placed the `JwtAuthenticationFilter` and the security filter chain in `auth`. Two problems
showed up when Phase 1 started:

1. **Build order.** `user` is built before `auth` (`auth → user`), but `user`'s endpoints already
   need authentication, role checks and `CurrentUser`.
2. **Web tests.** Every module's `@WebMvcTest` needs the real filter chain to test 401, 403 and
   404. If the chain lived in `auth`, each module's tests would import `auth` internals, which
   `CLAUDE.md` forbids ("Bypass a module's `api/` package, even in tests").

`common` already defines a `TokenAuthenticator` SPI for STOMP `CONNECT` (ADR-002), which `auth`
implements with `JwtService`.

## Decision

- **`common` owns the HTTP security setup** (`common.config.SecurityConfig`): stateless chain,
  CSRF, form login and HTTP Basic off, `@EnableMethodSecurity`, the exact-path public allowlist
  and the non-prod Swagger rule.
- **`common.security.BearerTokenAuthenticationFilter`** reads `Authorization: Bearer …` and calls
  the **same `TokenAuthenticator` SPI** used for STOMP. It stores an `AuthenticatedUser(id, role,
  expiresAt)` with the authority `ROLE_<role>`.
- **`auth` implements `TokenAuthenticator`** with jjwt (`JwtService`): signature, `exp`, `iss`.
  It throws `InvalidTokenException.expired()` (→ `401 TOKEN_EXPIRED`) or `.invalid()`
  (→ `401 UNAUTHORIZED`). If no implementation is present, every bearer token is rejected.
- **401 and 403 bodies** come from `ProblemDetailSecurityHandler`, so they match every other error.
  `GlobalExceptionHandler` rethrows security exceptions so Spring Security decides between 401
  (anonymous) and 403 (wrong role).
- **Public path variables match UUIDs only**, so `GET /restaurants/me` can't match the public
  `GET /restaurants/{id}`.
- **Tests** import `common`'s `WebSecurityTestConfig`, which uses the real chain with a fake
  `TokenAuthenticator` (`Bearer <ROLE>:<userId>`).

## Alternatives considered

### Chain in `auth`, modules test with `@WithMockUser`
- **Rejected:** `@WithMockUser` skips the real filter chain, so the 401/`TOKEN_EXPIRED` paths and
  the allowlist aren't tested per module, and `CurrentUser` would need a second principal type.

### Build `auth` before `user`
- **Rejected:** `auth` calls `UserApi.createUser`, so it can't come first without stubbing `user`.

## Consequences

- **Positive:** one chain, one allowlist, one principal type for HTTP and STOMP. Every module
  tests security against the real chain. `common` still doesn't depend on `auth`.
- **Negative:** the public allowlist in `common` lists other modules' paths as strings. Adding a
  public endpoint means editing `SecurityConfig` and `docs/SECURITY.md` §2 together.
- Supersedes the "`JwtAuthenticationFilter` in `auth`" part of ADR-002. Issuing tokens, jjwt
  and `JwtService` stay as ADR-002 describes.
