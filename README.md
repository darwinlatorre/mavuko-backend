# Mavuko backend foundation

Java 21 / Spring Boot 4.1 modular monolith. The initial `subscriptions/domain`
catalog contains the three documented plans. It does not activate subscriptions,
charge customers, persist data or provide private business endpoints.

## Test and build

With Maven 3.6.3+ and Java 21:

```sh
mvn -Dtest=PlanCatalogTest test
mvn verify
```

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
and its inbox to `http://localhost:8025`. Infrastructure is provisioned only;
the application has no database, Flyway or email adapter yet.
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

## Scope and next unit

The governing documents are maintained in the parent workspace's `docs/`:
`Mavuko_contexto_funcional_v1.4.md`, `Mavuko_arquitectura_general.md` and
`Mavuko_expansiones_futuras.md`. Functional rules take precedence; future features
are excluded. The backend is an independent Git repository.

Next: company persistence/Flyway and authenticated, audited manual subscription
activation. Auth0 integration, company/role authorization and catalog management
must exist before exposing private operations. Payments and activation remain
separate. DIAN, MFA and automatic SaaS checkout/renewal are not current scope.
