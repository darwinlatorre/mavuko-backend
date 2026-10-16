# Mavuko backend foundation

Java 21 / Spring Boot 4.1 modular monolith. The initial `subscriptions/domain`
catalog contains the three documented plans. It does not activate subscriptions,
charge customers or provide private business endpoints. An opt-in `persistence`
profile stores basic company identity using Spring Data JPA, PostgreSQL and Flyway.

## Test and build

With Maven 3.6.3+ and Java 21:

```sh
mvn -Dtest=PlanCatalogTest test
mvn verify
```

Run integration tests with local Java 21, Maven and Docker available:

```sh
mvn -Pintegration-tests verify
```

These tests create and remove their own PostgreSQL 18 container. They do not use
the development Compose database. Plain `mvn verify` and Docker image builds run
unit/context tests only; integration verification is a separate required check
for persistence changes. In PowerShell, quote dotted Maven options, for example
`mvn -Pintegration-tests "-Dit.test=CompanyPersistenceIT" verify`.

Without local Maven, run from this repository in PowerShell:

```powershell
docker run --rm --mount "type=bind,source=$($PWD.Path),target=/workspace" --mount "type=volume,source=mavuko-maven-cache,target=/root/.m2" -w /workspace maven:3.9.11-eclipse-temurin-21 mvn -B -ntp verify
```

## Local infrastructure

Copy `.env.example` to `infra/.env` and set a private `POSTGRES_PASSWORD` in
the copy. The example deliberately leaves the password empty; real environment
files are ignored by Git. Then run:

```sh
docker compose -f infra/compose.dev.yml config --quiet
docker compose -f infra/compose.dev.yml up -d
```

PostgreSQL 18 binds to `127.0.0.1:5432`; Mailpit SMTP to `127.0.0.1:1025`
and its inbox to `http://localhost:8025`. The database is used only when the
application's `persistence` profile is explicitly enabled. No email adapter exists.
`docker compose -f infra/compose.dev.yml stop` stops services without deleting data.
Do not use `down -v` unless you intend to delete the development database.

## Run the foundation

```sh
docker build -t mavuko-backend:dev .
docker run --rm -p 127.0.0.1:8080:8080 mavuko-backend:dev
```

The application starts on port 8080. With no API controllers, `/api/v1` returns
404; that is expected, not a functional product API. Local JVM execution binds
to loopback by default. Do not publish this unauthenticated foundation externally.
The `dev` and `prod` profiles are configuration placeholders, not deployments.

## Enable company persistence locally

After starting the local database, set `JDBC_DATABASE_URL` to
`jdbc:postgresql://127.0.0.1:5432/mavuko`, `POSTGRES_USER` to the configured user,
and `POSTGRES_PASSWORD` to your private development password in the process
environment. Compose reads `infra/.env`, but does not export it to your shell.
Do not commit credentials or use a production database for local tests.

```sh
mvn spring-boot:run "-Dspring-boot.run.profiles=persistence"
```

Flyway applies `db/migration/V1__create_companies.sql` on startup, before the
repository is available. Spring Boot auto-configures the datasource, Flyway and
JPA; Hibernate validates the migrated schema rather than creating it. Hikari
limits the pool to five connections. Missing or invalid connection settings use
Spring Boot/database startup diagnostics; Flyway clean is disabled. The default
profile remains database-free. Use the local JVM command above for persistence;
the Docker run example does not connect to host Compose automatically.

The internal repository stores UUID, legal name and optional tax identifier.
It is not an authorization boundary, creates no subscription or payment, and
is not exposed over HTTP. Owner membership, company status/audit workflows,
tax validation and the rest of the company profile remain subsequent work.

### Persistence patterns

- Keep the domain repository port independent of JPA. An explicit mapper separates
  the domain record from its persistence entity; consider MapStruct only when
  mapping volume makes generated mappings worthwhile.
- Keep explicit `@Transactional` boundaries in application use cases:
  `CompanyService.add` starts a write transaction and `findById` uses a read-only
  transaction. Adapters have no custom transaction annotations; Spring Data's
  built-in repository transactions join the application transaction. Do not put
  persistence transactions in controllers.
- Add request/response DTOs when HTTP boundaries exist; never expose JPA entities.
- Use builders for genuinely complex optional construction, not simple records.
  Lombok is optional for repetitive boilerplate; avoid entity `@Data` and generated
  equality or string methods that traverse lazy relationships.
- Introduce Spring Data Specifications only for composable dynamic filters. The
  current identity lookup needs neither Specifications nor a generic query layer.

## Scope and next unit

The governing documents are maintained in the parent workspace's `docs/`:
`Mavuko_contexto_funcional_v1.4.md`, `Mavuko_arquitectura_general.md` and
`Mavuko_expansiones_futuras.md`. Functional rules take precedence; future features
are excluded. The backend is an independent Git repository.

Next: Auth0-backed authorization, company membership and authenticated, audited
manual subscription activation. Company/role authorization and catalog management
must exist before exposing private operations. Payments and activation remain
separate. DIAN, MFA and automatic SaaS checkout/renewal are not current scope.
