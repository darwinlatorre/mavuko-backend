# Mavuko Backend

Java 21 and Spring Boot modular monolith for Mavuko. The backend includes a
database-managed subscription plan catalog, company persistence and exact company
membership queries. HTTP access is closed by default; the authenticated company
selector is available only with persistence and token validation configured.

## Requirements

- Java 21
- Maven 3.6.3 or newer
- Docker for local infrastructure and integration tests

## Run locally

```sh
mvn spring-boot:run
```

The application listens on port 8080 and binds to loopback by default. The default
profile does not require a database. Unauthenticated HTTP requests return 401.

### Local database

Copy `.env.example` to `infra/.env` and set your own `POSTGRES_PASSWORD`.
Do not commit real environment files or credentials.

```sh
docker compose -f infra/compose.dev.yml config --quiet
docker compose -f infra/compose.dev.yml up -d
```

Services are available locally at:

| Service | Address |
| --- | --- |
| PostgreSQL 18 | `127.0.0.1:5432` |
| Mailpit SMTP | `127.0.0.1:1025` |
| Mailpit inbox | `http://localhost:8025` |

To enable persistence, set these variables in the application process environment:

| Variable | Value |
| --- | --- |
| `JDBC_DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5432/mavuko` |
| `POSTGRES_USER` | Your local database user |
| `POSTGRES_PASSWORD` | Your local database password |

Compose reads `infra/.env` but does not export it to your shell.

```sh
mvn spring-boot:run "-Dspring-boot.run.profiles=persistence"
```

Flyway applies database migrations on startup. Stop the local services without
deleting their data with:

```sh
docker compose -f infra/compose.dev.yml stop
```

### Token validation configuration

Set both `AUTH0_ISSUER` and `AUTH0_AUDIENCE` in the application process environment
to configure bearer-token validation. The issuer must be an HTTPS origin ending
in `/`, and the audience must identify your application API. Missing configuration
keeps HTTP access closed. With `persistence` enabled, a verified token can access
only the company selector; other business operations remain denied. See the
[company selection API](docs/company-selection-api.md) for its contract and limits.

## Tests and build

Run unit and application context tests:

```sh
mvn verify
```

Run integration tests with Docker available:

```sh
mvn -Pintegration-tests verify
```

Integration tests create their own PostgreSQL 18 Testcontainers database and do
not use the local Compose database. In PowerShell, quote dotted Maven options:

```powershell
mvn -Pintegration-tests "-Dit.test=CompanyPersistenceIT" verify
```

## Docker

```sh
docker build -t mavuko-backend:dev .
docker run --rm -p 127.0.0.1:8080:8080 mavuko-backend:dev
```

This example uses the database-free default profile; it does not automatically
connect to the local Compose database.

## Structure

```text
src/main/java/com/mavuko/  Application modules
src/main/resources/       Application configuration and database migrations
src/test/                 Unit, application context and integration tests
infra/                    Local Docker Compose infrastructure
pom.xml                   Maven dependencies and build configuration
Dockerfile                Application container image
```

## Persistence design

Spring Boot configures the datasource, Flyway, Hibernate and Spring Data JPA;
there is no manual persistence configuration class. The default profile excludes
datasource auto-configuration, and `persistence` opts back in using the existing
`mavuko.database` properties. Flyway owns schema changes; Hibernate only validates
them. Open-in-view is disabled, and Flyway clean is disabled.

`CompanyService` owns application transaction boundaries: `add` uses
`@Transactional`, and `findById` uses `@Transactional(readOnly = true)`. These
boundaries belong to application use cases, not HTTP controllers or persistence
adapters. `JpaCompanyRepository` implements the unchanged domain port without
custom transaction annotations; Spring Data's framework transactions remain and
join the application transaction. Assigned UUID entities explicitly report newness
so a duplicate insert fails instead of overwriting stored company data.

See [Boot-managed JPA persistence](docs/persistence.md) for configuration,
repository and application transaction details.

## Company memberships and plan catalog

With `persistence` enabled, Flyway V2 adds `company_memberships` and the
`AuthorizedCompanies` query port. Lookup uses the exact `(issuer, subject)` pair,
not email or subject alone, and returns only explicitly granted companies ordered
by legal name and ID. Identity columns use case-sensitive bytewise collation;
company grants are unique and reference existing companies. Reads create no data
and do not require a subscription. V1 and existing company records are unchanged;
upgrading creates no grants, so existing companies are not implicitly accessible.

The [company selector](docs/company-selection-api.md) supplies the validated
token identity to this query; membership is not an operation-permission policy.
Roles, site permissions, grant issuance/revocation and audit history,
invitations, owner assignment and first-login provisioning remain pending.
No RLS or production database-role separation is claimed by this local foundation.
Rolling back application code must preserve applied migrations and grant data;
there is no automatic destructive down migration.

Flyway V3 seeds the commercial plan catalog once. Current offers are read from
PostgreSQL through read-only application transactions, without a static fallback
or cache. See [plan catalog maintenance](docs/plan-catalog.md); do not edit applied
migrations to change offers. No subscriptions, payments or activation are created.

## Implementation guidance

- **Mappers:** keep JPA entities behind the domain boundary. The small explicit
  mapper is sufficient now; consider MapStruct when mappings become repetitive.
- **Builders:** use them for complex construction or test fixtures, not as a
  substitute for constructors that enforce domain invariants.
- **Lombok:** optional for repetitive boilerplate; avoid entity `@Data` and generated
  equality or string methods that traverse lazy associations.
- **DTOs:** introduce request/response records when HTTP endpoints are added;
  never expose JPA entities as API contracts.
- **Specifications:** add them when composable dynamic filters are required.
  Simple identity lookups only need the plain Spring Data repository.
