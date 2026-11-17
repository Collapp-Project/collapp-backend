# Collapp Backend

REST API for a marketplace connecting creative professionals (makeup artists, photographers,
models, producers) with collaboration offers. Java 25, Spring Boot 3.5.16, PostgreSQL, JWT.

## Commands

```bash
./mvnw test                  # unit tests (JUnit 5 + Mockito)
./mvnw spring-boot:run       # run locally on :8080
./mvnw verify                # full build
```

Configuration comes from `.env` in the project root (see `.env.example`). The project uses
`spring-dotenv`, so Spring reads that file directly as properties — an exported shell variable
is **not** enough to override it, and a value in `.env` applies even when the environment
does not set it.

`SPRING_PROFILES_ACTIVE=dev` enables `DataSeeder`, which inserts sample users with known
passwords. No profile is active by default, so seeding requires opting in. Never enable it in
a deployed environment.

## Conventions

**Git — commit messages and branch names are written in English**, even though code comments
and discussion are in Spanish. Branches are `feature/kebab-case` or `fix/kebab-case`; commits
follow Conventional Commits (`fix:`, `feat:`, `docs:`). **Never add a `Co-Authored-By` line or
any other AI attribution** to commits or pull request descriptions.

**Javadoc is minimal.** Write it only when it clarifies something the signature does not, and
keep it to a single line whenever possible. Document the non-obvious *why* — a deliberate
decision, a known pitfall, an invariant — never restate the method name.

**Layering:** `controller` → `service` (interface + `impl`) → `repository`. Controllers hold no
business logic; services never build entities by hand, that belongs to the mappers in `mapper/`.

**DTOs are records**, grouped by feature under `dto/`. Requests carry Bean Validation
annotations; entities are never exposed directly.

**Entities** deliberately avoid Lombok's `@Data`: it breaks `hashCode` on JPA identity and
makes `toString` touch LAZY collections. Keep `@Getter @Setter`, `@EqualsAndHashCode(onlyExplicitlyIncluded = true)`
and `@ToString(exclude = ...)` as they are.

## Code style

Readability over cleverness. Prefer verbose, structured code to compact or "smart" one-liners:
a reader should never have to decode an expression.

- **Single responsibility.** One function does one thing. Services orchestrate, mappers convert,
  repositories query — no method mixes those roles.
- **Named intermediate variables.** Break long or nested expressions into steps whose names
  explain the intent, instead of chaining everything into one statement.
- **Low cyclomatic complexity.** No chains of `if`, no nested loops, no logic buried inside
  lambdas. If a method needs comments to explain its branches, split it.
- **Self-explanatory names.** The name carries the meaning, so the code needs no Javadoc to be
  understood (see the Javadoc rule above).
- **SOLID**, in practice: depend on the service interface rather than the `Impl`; a class that
  needs "and" to describe what it does should be two classes.

## Error contract

Every error response is an `ApiError` (`dto/error/ApiError.java`):

```json
{ "timestamp": "...", "status": 400, "error": "VALIDATION_ERROR",
  "message": "text for the user", "path": "/api/...", "fields": { "email": "..." } }
```

`error` is a stable machine-readable code, `message` is the human-readable text. `fields` is
only present on validation 400s; validation errors not tied to a field go under the `_form` key
(`ApiError.FORM_ERROR_KEY`). The frontend depends on this shape — changing it is a breaking
change that must ship before the frontend adapts.

New exceptions get a handler in `GlobalExceptionHandler`. Errors raised inside the Spring
Security filter chain bypass `@RestControllerAdvice`, so they are written by `ApiErrorWriter`
through `RestAuthenticationEntryPoint` and `RestAccessDeniedHandler`.

## Testing

Tests are unit tests with Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`).
Shared fixtures live in `util/TestObjectFactory`. Test names are descriptive sentences in Spanish.

There are no integration tests yet, so routing, security and serialisation are currently
unverified by the suite — see `docs/ROADMAP.md`, task 3.2.

## Known gaps

`docs/ROADMAP.md` tracks the planned work. The most relevant open items: CORS origins are
hardcoded in `SecurityConfig` (1.12), there is no `@Transactional` anywhere and `open-in-view`
is still on (1.4), listings have N+1 queries (1.5), and the schema is managed by
`ddl-auto=update` instead of migrations (1.7).

Offer `location` is now the `Location` enum (Spanish provinces): any pre-existing rows with
free-text locations in a dev database must be re-seeded or updated, or reads will fail. The
frontend must send enum names (e.g. `"BARCELONA"`), not free text.
