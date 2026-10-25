# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

vsnvi-food is a food-delivery backend (Zomato-style), built as a Spring Boot 4.1 / Java 25
**modular monolith**. The repo is still a skeleton: only `VsnviFoodApplication` and the generated
Testcontainers test setup exist. The design lives in `docs/` and is the source of truth for what
gets built:

| Doc | Covers |
|---|---|
| `docs/ARCHITECTURE.md` | Modules, dependency graph, events, order state machine, tracking (Redis GEO + STOMP), Razorpay payment flow, pricing and coupons |
| `docs/API-DESIGN.md` | URL rules, ProblemDetail errors and error codes, pagination, idempotency, endpoint list, STOMP destinations, OpenAPI/springdoc |
| `docs/DATABASE-DESIGN.md` | Entities per module and relationships. **Flyway migrations are the schema source of truth** |
| `docs/SECURITY.md` | JWT and refresh-token flow, roles and ownership, Razorpay signatures, secrets, WebSocket auth |
| `docs/PROGRESS.md` | Phase checklist. Tick items when they are merged |
| `docs/decisions/` | ADRs. Add one for every significant architectural decision |

Coding, testing, persistence and security rules are in `.claude/rules/` (`java.md`,
`testing.md`, `persistence.md`, `security.md`). `security.md` always applies; the others load when
you work on matching files.

## Workflow

- Read `docs/PROGRESS.md` and the relevant doc section before starting a task.
- For changes touching more than one module, propose a plan before writing code.
- Every change ships with tests; `./mvnw verify` must pass before work is done.
- Keep changes small and focused on one task.
- Tick the item in `docs/PROGRESS.md`, and update affected docs in the same change. If code needs
  to diverge from the docs, change the docs, and add an ADR in `docs/decisions/` for significant
  decisions.
- Branches: `feature/<module>-<short-desc>`, `fix/<short-desc>`, `chore/<short-desc>`. Commits
  follow Conventional Commits, e.g. `feat(order): add cancel endpoint`.

## Commands

- **Build and run all tests:** `./mvnw verify`.
- **Tests only:** `./mvnw test`.
- **One test:** `./mvnw test -Dtest=ClassName` or `./mvnw test -Dtest=ClassName#methodName`.
- **Run locally (persistent data):**
  1. `docker compose up -d` starts Postgres 18, Redis 8.2 and Mailpit from `compose.yaml`.
  2. `./mvnw spring-boot:run` (or Run in the IDE). `local` is the default profile, and its
     defaults match the Compose containers, so no setup is needed. To override values, use a
     git-ignored `.env` (template: `.env.example`) or environment variables.
  - App: `http://localhost:8080`. Health: `/actuator/health`. Swagger UI: `/swagger-ui.html`.
    Mailpit inbox: `http://localhost:8025`.
  - `docker compose down` stops the stack; `docker compose down -v` also wipes the data.
- **Run locally against a throwaway Postgres container:** `./mvnw spring-boot:test-run`, which
  starts `TestVsnviFoodApplication` with `TestcontainersConfiguration` (no `.env` needed).
- **Compile only:** `./mvnw -DskipTests compile`.
- **Format:** `./mvnw spotless:apply` (palantir-java-format, unused imports removed);
  `./mvnw spotless:check` to check only. A PostToolUse hook (`.claude/hooks/format-java.sh`)
  formats each Java file Claude writes or edits, so don't hand-format.

Tests and the local run need a running Docker daemon (Testcontainers or Compose). There is no
linter configured yet.

## Configuration

- One `src/main/resources/application.yaml` with profile documents: `local` (the default when
  none is active; tests run with it too, but Testcontainers replaces the database connection),
  `dev`, `test` (API docs on, Swagger UI off) and `prod` (everything from the environment, API
  docs off). Deployments must set `SPRING_PROFILES_ACTIVE`.
- Secrets are `${ENV_VAR}` placeholders. Only `local` gives them throwaway defaults that match
  `compose.yaml`. The list is in `docs/SECURITY.md` §4. Non-secret tunables (`auth.jwt.*` TTLs,
  `pricing.tax-percent`, `delivery.assignment.*`) have defaults in the yaml.
- When you add a technology that needs a connection (a new datastore, broker or mail provider),
  add its container to `compose.yaml`, its properties with working defaults to the `local` document and without defaults to
  the `dev, prod` document,
  its variables to `.env.example` and `docs/SECURITY.md` §4, and update this section.
- Razorpay has no container. Tests use `FakePaymentGateway`; a local run uses test-mode keys.

## Architecture in brief

- **Modules:** each module is a direct sub-package of `com.vsnvi.vsnvi_food`: `auth`, `user`,
  `restaurant`, `menu`, `cart`, `order`, `payment`, `delivery`, `promotion`, `notification`, and
  `common` (a shared kernel with no business logic).
- **Module layout:** each module has `api/` (public: the `<Module>Api` facade, its DTOs and
  events), `api/web/` (controllers, internal), `application/`, `domain/` and `infrastructure/`.
  Other modules may use only `api/`.
- **Boundaries are enforced by Spring Modulith:** `ApplicationModules.verify()` runs in tests,
  with `@NamedInterface("api")` on each module's `api` package.
- **The module graph must be acyclic.** Listening to an event counts as a dependency, so **calls
  go down the graph and events go up**. For example:
  - `order` calls `PaymentApi.cancelPayment` instead of `payment` listening to `OrderCancelled`.
  - `delivery` calls `OrderApi.markPickedUp`, and then `order` publishes `OrderPickedUp`.

  Check the dependency table in `docs/ARCHITECTURE.md` §2 before adding any cross-module call or
  listener.
- **Events:**
  - Cross-module events go through Modulith's Event Publication Registry, which uses the
    `event_publication` table created by our own Flyway migration because `ddl-auto=validate`.
  - Consumers use `@ApplicationModuleListener` and must be idempotent, because delivery is at
    least once.
- **Single places for core logic:**
  - Order status changes only through methods on the `Order` aggregate.
  - Coupon discounts are calculated only by `promotion`; every other price line and the total
    only by `CartPricing` in `cart`.
  - For online payments, a verified Razorpay webhook is the only thing that moves an order to
    `PLACED`. Cash-on-delivery orders are placed at checkout.

## Repo notes

- **Dependencies:** `pom.xml` has Spring Modulith 2.1.2, jjwt 0.13.0 with `jjwt-gson` (ADR-002; not
  Spring's OAuth2 resource server), Redis, WebSocket and springdoc. Razorpay comes with the payment
  module.
- **Stale classes:** the IDE's Java extension also compiles into `target/`. If tests fail with odd
  reflection errors (e.g. `TypeNotPresentException`) or "Unresolved compilation problem", run
  `./mvnw clean verify`.
- **Base package:** it has an underscore (`vsnvi_food`), because the generated project name
  contains a hyphen.

## Do not

- Add a dependency to `pom.xml` without asking first.
- Change an endpoint's contract without updating `docs/API-DESIGN.md`.
- Bypass a module's `api/` package, even in tests.
