# Boot-managed JPA persistence

Spring Boot auto-configures the datasource, Hibernate, Spring Data repositories,
transaction manager and Flyway. No `CompanyPersistenceConfiguration` is needed.

## Configuration and safety

The default profile excludes datasource auto-configuration and remains
database-free. The `persistence` profile enables it and supplies connection and
pool properties. Existing `JDBC_DATABASE_URL`, `POSTGRES_USER`, `POSTGRES_PASSWORD`
environment inputs and `mavuko.database.*` property overrides remain supported.
HikariCP caps the pool at five connections, with one minimum idle. Connection
failures use Boot/Hikari/Flyway diagnostics, not a custom credential validator.

Flyway runs before Hibernate schema validation. Hibernate never creates or updates
tables; Flyway clean is disabled and applied migrations remain unchanged.
Open EntityManager in View is disabled.

## Repository and transaction boundaries

`CompanyJpaRepository` handles database access. `JpaCompanyRepository` and
`JpaAuthorizedCompanies` implement the existing ports and map entities through
`CompanyMapper`. The domain remains independent of JPA. Assigned UUIDs use explicit
new-entity detection and flush so duplicate inserts fail instead of overwriting.
The parameterized native JPA membership query preserves exact opaque identity
matching and PostgreSQL ordering. Production adapters no longer use JDBC;
`JdbcTemplate` remains available for integration fixtures and assertions.

Application use-case implementations own `@Transactional`: `CompanyService.add`
is read-write, while `CompanyService.findById` and
`AuthorizedCompaniesService.findFor` are read-only. These services delegate to
the existing ports and introduce no registration or permission-granting workflow.
Persistence adapters and HTTP controllers do not define transaction boundaries.
Spring Data repositories retain their framework-provided transaction behavior and
join an existing application transaction. Application services can therefore
coordinate multiple repository calls in a single transaction.

## Pattern recommendations

| Pattern | Recommendation |
| --- | --- |
| Mappers | Explicit mapping now; consider MapStruct when mappings become numerous, not for three fields. |
| DTOs | Separate request/response records when HTTP endpoints exist; never expose JPA entities. |
| Builders | Useful for many optional fields; retain validated constructors for small domain records. |
| Lombok | Optional for repetitive boilerplate. Avoid entity `@Data` and generated relationship equality/toString. |
| Specifications | Use for composable dynamic filters, not the fixed membership query; never replace authorization constraints. |
