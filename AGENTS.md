# Collapp Backend

REST API for Collapp, a collaboration platform for audiovisual creatives (makeup,
photography/video, modeling, production). Users register, publish project offers,
apply to them and manage the applications they receive.

Java 25 · Spring Boot 3.5.16 · PostgreSQL · JWT (jjwt) · Maven.

## Commands

```bash
./mvnw test                  # unit + integration tests
./mvnw spring-boot:run       # run locally on :8080
./mvnw verify -Pci           # full build with the 70% line-coverage gate
```

## Configuration

Variables come from `.env` in the project root (copy `.env.example`). The project uses
`spring-dotenv`, so Spring reads that file directly as properties — an exported shell
variable is **not** enough to override it, and a value in `.env` applies even when the
environment does not set it.

| Variable | Purpose |
|---|---|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection |
| `JWT_SECRET` | Secret for signing tokens (HS256, min 32 bytes) |
| `SPRING_PROFILES_ACTIVE` | `dev` enables the DataSeeder; empty by default |

`SPRING_PROFILES_ACTIVE=dev` runs `DataSeeder`, which inserts sample users with known
passwords into an empty database. Never enable it in a deployed environment.

## Conventions

**Git — commit messages and branch names are written in English**, even though code
comments and discussion are in Spanish. Branches are `feature/kebab-case` or
`fix/kebab-case`; commits follow Conventional Commits (`fix:`, `feat:`, `docs:`,
`refactor:`, `chore:`, `test:`). **Never add a `Co-Authored-By` line or any other AI
attribution** to commits or pull request descriptions.

**Javadoc is minimal.** Only when it clarifies something the signature does not; single
line when possible. Document the non-obvious *why* — a deliberate decision, a known
pitfall, an invariant — never restate the method name.

**Layering:** `controller` → `service` (interface + `impl`) → `repository`. Controllers
hold no business logic; services orchestrate and authorize but **never build or update
entities by hand** — that belongs to the mappers in `mapper/` (`toEntity` on creation,
`updateEntity` on edit). Repositories only query.

**DTOs are records**, grouped by feature under `dto/`. Requests carry Bean Validation;
entities are never exposed directly. Cross-field validation is a class-level constraint
in `validation/` (`@ValidDateRange`), whose errors surface under the `ApiError` `_form` key.

**Entities** deliberately avoid Lombok `@Data`: it generates `equals`/`hashCode` over
unstable fields and a `toString` that touches LAZY collections. Keep `@Getter @Setter`,
`@EqualsAndHashCode(onlyExplicitlyIncluded = true)` and `@ToString(exclude = ...)`.

**Enums** in `entity/enums/`: `SystemRole`, `Specialty`, `CompensationType`, `OfferStatus`,
`ApplicationStatus` and `Location` (the 52 Spanish provinces). Names are ASCII with
underscores (`A_CORUNA`, `CIUDAD_REAL`) and are part of the API contract, serialized
by name.

## Code style

Readability over cleverness. Prefer verbose, structured code to compact or "smart"
one-liners: a reader should never have to decode an expression.

- **Single responsibility.** One function does one thing.
- **Named intermediate variables.** Break long or nested expressions into steps whose
  names explain the intent.
- **Low cyclomatic complexity.** No chains of `if`, no nested loops, no logic buried
  inside lambdas.
- **Self-explanatory names.** The name carries the meaning.
- **SOLID**, in practice: depend on the service interface rather than the `Impl`.

## Error contract

Every error response is an `ApiError` (`dto/error/ApiError.java`):

```json
{ "timestamp": "...", "status": 400, "error": "VALIDATION_ERROR",
  "message": "text for the user", "path": "/api/...", "fields": { "email": "..." } }
```

`error` is a stable machine-readable code, `message` is the human-readable text. `fields`
appears only on validation 400s; errors not tied to a field go under the `_form` key
(`ApiError.FORM_ERROR_KEY`). Errors raised inside the Spring Security filter chain bypass
`@RestControllerAdvice`, so they are written by `ApiErrorWriter` through
`RestAuthenticationEntryPoint` and `RestAccessDeniedHandler`. New exceptions get a handler
in `GlobalExceptionHandler`.

## Security notes

- Public routes: `POST /api/auth/**`, `GET /api/offers` and `GET /api/offers/{id}`, plus
  the OpenAPI docs (`/swagger-ui/**`, `/v3/api-docs/**`). `GET /api/offers/all` is
  admin-only and `GET /api/offers/mine` requires auth — both are matched **before** the
  public wildcard in `SecurityConfig`, and that order matters.
- The JWT subject is the user email; claims include `role`.
- `JwtAuthFilter` swallows invalid/expired tokens and leaves the context empty, so the
  `ExceptionTranslationFilter` returns the 401.

## Testing

Unit tests with Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`/`@InjectMocks`)
cover services and mappers. `integration/ApiIntegrationTest` runs end-to-end against a
real PostgreSQL via Testcontainers (skipped automatically without Docker): it validates
routing, security and serialisation. Shared fixtures live in `util/TestObjectFactory`.
**Test names are descriptive sentences in Spanish.** When stubbing mappers, return
fixtures consistent with the input (the real mapper derives entities from the request).

Coverage is measured with JaCoCo (`target/site/jacoco`, currently ~86% of lines). The
70% gate only applies with `-Pci`, so local builds do not fail without Docker.

## Known gaps

`docs/ROADMAP.md` tracks planned work with ✅/⚠️ progress marks. Currently open, in
priority order: CORS origins hardcoded in `SecurityConfig` (1.12), no `@Transactional`
and `open-in-view` still on (1.4), N+1 queries on listings (1.5), `ddl-auto=update`
instead of Flyway (1.7), pagination only on the public listing (2.3), refresh tokens
and login rate limiting (1.8/3.6).

## Frontend contract

The React/Vite frontend lives in the `collapp-frontend` repo (CORS is currently pinned
to `http://localhost:5173`). Backend-facing notes: offer `location` must be sent and is
returned as a `Location` enum name; validation errors arrive in `ApiError.fields`, with
class-level constraints under `_form`.
