# Mavuko backend foundation

Java 21 / Spring Boot 4.1 modular monolith. The initial `subscriptions/domain`
catalog contains the three documented plans. It does not activate subscriptions,
charge customers or provide private business endpoints. An opt-in `persistence`
profile stores basic company identity using PostgreSQL and Flyway.

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
401 without a verified bearer token; that is expected, not a functional product
API. Local JVM execution binds to loopback by default. Do not publish this
incomplete application externally.
The `dev` and `prod` profiles are configuration placeholders, not deployments.

## JWT boundary (not yet a business API)

HTTP access is closed by default, even without Auth0 configuration. Set both
`AUTH0_ISSUER` and `AUTH0_AUDIENCE` in the backend process environment to enable
bearer-token verification. Setting only one, or an unsafe issuer, fails startup.
The issuer must be an HTTPS origin ending in `/`; the authorized development
tenant uses `https://dev-bjqmy40kw8nsidwf.us.auth0.com/`. The audience must be the
identifier of the Mavuko API once provisioned, **not** Auth0 Management API.
No real audience or SPA client ID has been provisioned by this unit.

The decoder obtains public keys from the issuer's `.well-known/jwks.json` over
HTTPS, accepts RS256 only, and checks issuer, audience, required subject and
expiration, and any not-before claim. Timestamp validation allows Spring
Security's default 60-second clock skew. JWKS connections/read operations have
3/5-second timeouts. Startup does not contact Auth0; token validation needs its
public key endpoint when the key is not cached.

Only `Authorization: Bearer ...` authenticates requests: no form login, Basic
authentication, cookie/query tokens or backend HTTP session. Without a verified
identity responses are 401; verified tokens still receive 403 because no business
authorization policy exists yet. Even an `admin` claim grants no access by itself.
CSRF is disabled for this header-only stateless API; revisit this decision before
introducing browser-cookie authentication. CORS and frontend login are not wired.
The automatic public OAuth protected-resource metadata filter is excluded from
the chain because it responds before authorization, even with `denyAll` configured.

Run `mvn "-Dtest=ClosedSecurityTest,JwtBoundaryTest,ClosedMetadataTest" test` for the security tests.
They use ephemeral local RSA keys and an in-memory JWKS HTTP stub, not Auth0
credentials or a live tenant. Company memberships, company/site policies and
global administration rules must be implemented before allowing any endpoint.
The agreed Auth0.js Embedded Login integration remains unchanged and pending.

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
repository is available. Hikari limits the pool to five connections. Missing or
blank connection settings fail startup; Flyway clean is disabled. The default
profile remains database-free. Use the local JVM command above for persistence;
the Docker run example does not connect to host Compose automatically.

The internal repository stores UUID, legal name and optional tax identifier.
It is not an authorization boundary, creates no subscription or payment, and
is not exposed over HTTP. Owner membership, company status/audit workflows,
tax validation and the rest of the company profile remain subsequent work.

## Explicit company memberships (internal only)

With `persistence` enabled, Flyway V2 adds `company_memberships` and the
`AuthorizedCompanies` query port. Lookup uses the exact `(issuer, subject)` pair,
not email or subject alone, and returns only explicitly granted companies ordered
by legal name and ID. Identity columns use case-sensitive bytewise collation;
company grants are unique and reference existing companies. Reads create no data
and do not require a subscription. V1 and existing company records are unchanged;
upgrading creates no grants, so existing companies are not implicitly accessible.

This is not an HTTP API, a JWT verifier or an operation-permission policy. Its
caller must supply an already authenticated identity. The HTTP boundary remains
closed. Roles, site permissions, grant issuance/revocation and audit history,
invitations, owner assignment and first-login provisioning remain pending.
No RLS or production database-role separation is claimed by this local foundation.
Rolling back application code must preserve applied migrations and grant data;
there is no automatic destructive down migration.

## Scope and next unit

The governing documents are maintained in the parent workspace's `docs/`:
`Mavuko_contexto_funcional_v1.4.md`, `Mavuko_arquitectura_general.md` and
`Mavuko_expansiones_futuras.md`. Functional rules take precedence; future features
are excluded. The backend is an independent Git repository.

Next: real Auth0 integration, company membership and authenticated, audited
manual subscription activation. Company/role authorization and catalog management
must exist before exposing private operations. Payments and activation remain
separate. DIAN, MFA and automatic SaaS checkout/renewal are not current scope.
