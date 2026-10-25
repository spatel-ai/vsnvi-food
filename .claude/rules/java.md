---
paths:
  - "src/**/*.java"
---

# Java rules

Details: `docs/ARCHITECTURE.md`, `docs/API-DESIGN.md`, `docs/DATABASE-DESIGN.md`.

## Module layout (base package `com.vsnvi.vsnvi_food`)

Modules: `auth`, `user`, `restaurant`, `menu`, `cart`, `order`, `payment`, `delivery`, `promotion`, `notification`, `common`.

```
<module>/
├── api/              <Module>Api facade interface, facade DTO records, published event records
│   └── web/          @RestController classes + request/response records
├── application/      services, @Transactional, <Module>Api implementation, @ApplicationModuleListener, @Scheduled jobs
├── domain/           entities, value objects, enums, module exceptions, repository interfaces
└── infrastructure/   Spring Data JPA/Redis implementations, external clients (Razorpay), mappers, @Configuration
```

- From another module, import only classes directly in its `api` package. Never import its `api.web`, `application`, `domain` or `infrastructure` packages.
- `common` holds no business logic and depends on no other module. It uses its own layout (`domain/`, `web/`, `security/`, `idempotency/`, `ratelimit/`, `config/`; see `docs/ARCHITECTURE.md` §2), not the module layout above.
- Dependency direction: `api.web` → `application` → `domain`. `infrastructure` implements `domain` interfaces. Controllers never call repositories.
- `domain` must not import Spring Web, `application`, `infrastructure`, or other modules (except `common`).
- `infrastructure` contains no business rules. Business logic belongs in `application` services or in entity methods.
- Spring Modulith enforces the boundary. Annotate `api/package-info.java` with `@NamedInterface("api")`. `common` is `@ApplicationModule(type = OPEN)`.
- The module graph must stay acyclic. Listening to an event is a dependency just like a call. Calls go down the graph and events go up, and a module never listens to events of a module that depends on it.
- To make a module you depend on act, call its facade (e.g. `PaymentApi.cancelPayment`). To tell modules that depend on you, publish an event.
- Consume events with `@ApplicationModuleListener`, never with a plain `@EventListener`. Every listener must be idempotent.

## Code

- Use constructor injection only, via `@RequiredArgsConstructor` and `final` fields. No field `@Autowired`.
- Requests, responses, DTOs and events must be `record`s. Name events in the past tense and give them an `occurredAt` field.
- Controllers validate (`@Valid`), map and delegate. They contain no business logic and never return entities.
- Put `@Transactional` only on `application` services, and use `readOnly = true` for queries.
- Public methods never return `null`. Use `Optional`, or throw `NotFoundException`.
- Use `Instant` for timestamps (UTC) and `Money` for amounts. Never use `double`/`float` for money, or `LocalDateTime` for instants.
- Get the current time from an injected `Clock` bean (`Instant.now(clock)`). Never call `Instant.now()` or `LocalDate.now()` without a `Clock`, so tests can fix the time.
- Use Java 25 features (records, sealed types, pattern matching) where they make the code clearer.

## OpenAPI (springdoc)

- Every controller has `@Tag(name = "<module>")`; every handler has `@Operation(summary, operationId)` with an API-wide unique `operationId`.
- Follow the annotation conventions in `docs/API-DESIGN.md` §11 (responses, `@SecurityRequirements()`, `@IdempotencyKeyHeader`, `@Schema`, pagination).

## Entities

- Every entity extends `common` `BaseEntity` (UUID id, `createdAt`, `updatedAt`). `BaseEntity` has **no** `version` field.
- `@Version` is declared on `Order`, `Payment`, `Cart` and `DeliveryTask` only (their tables have a `version` column). Coupon redemption locks the `Coupon` row (`PESSIMISTIC_WRITE`).
- Allowed Lombok on entities: `@Getter`, `@NoArgsConstructor(access = PROTECTED)`, `@Builder` with `@AllArgsConstructor(access = PRIVATE)`. Never use `@Data`, `@Setter` or `@EqualsAndHashCode`.
- Change state only through intention-revealing methods (`order.accept()`), which enforce invariants.
- Use `@Enumerated(EnumType.STRING)` and lazy fetching. Never create a JPA relation to another module's entity; store its UUID instead.
- Every new or changed entity field needs a matching Flyway migration (`.claude/rules/persistence.md`).

## Errors and logging

- Domain exceptions extend `DomainException(ErrorCode, message)`. One `@RestControllerAdvice` maps them to `ProblemDetail`.
- Never swallow an exception. Log it once, at the boundary.
- Use `@Slf4j` with parameterized messages.

## Naming

- `<Resource>Controller`, `<Resource>Service`, `<Module>Api`, `<Entity>Repository`, `<Module>EventListener`, `<Reason>Exception`.
- Requests `<Action><Resource>Request`, responses `<Resource>Response`, events in the past tense (`PaymentSucceeded`).
