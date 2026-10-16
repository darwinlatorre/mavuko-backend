package com.mavuko.companies.infrastructure;

import java.util.List;
import java.util.UUID;

import com.mavuko.access.domain.ExternalIdentity;
import com.mavuko.companies.application.AuthorizedCompanies;
import com.mavuko.companies.domain.Company;
import com.mavuko.companies.domain.CompanyRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("persistence")
@Testcontainers
class CompanyMembershipPersistenceIT {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("mavuko.database.url", DATABASE::getJdbcUrl);
        properties.add("mavuko.database.username", DATABASE::getUsername);
        properties.add("mavuko.database.password", DATABASE::getPassword);
    }

    @Autowired CompanyRepository companies;
    @Autowired AuthorizedCompanies authorizedCompanies;
    @Autowired JdbcTemplate jdbc;

    @Test
    void returnsOnlyExplicitGrantsForTheExactIssuerAndSubjectWithoutWriting() {
        String subject = "User-" + UUID.randomUUID();
        var alice = new ExternalIdentity("https://issuer-a.example/", subject);
        var otherIssuer = new ExternalIdentity("https://issuer-b.example/", subject);
        var otherSubject = new ExternalIdentity(alice.issuer(), subject.toLowerCase(java.util.Locale.ROOT));
        Company first = company("A company");
        Company second = company("B company");
        Company foreign = company("C company");
        Company differentSubject = company("D company");
        company("No membership");
        grant(alice, second.id());
        grant(alice, first.id());
        grant(otherIssuer, foreign.id());
        grant(otherSubject, differentSubject.id());
        var beforeCompanies = jdbc.queryForList("select * from companies order by id");
        var beforeGrants = jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id");

        assertEquals(List.of(first, second), authorizedCompanies.findFor(alice));
        assertEquals(List.of(foreign), authorizedCompanies.findFor(otherIssuer));
        assertEquals(List.of(differentSubject), authorizedCompanies.findFor(otherSubject));
        assertTrue(authorizedCompanies.findFor(new ExternalIdentity(alice.issuer(), "unknown")).isEmpty());
        assertTrue(authorizedCompanies.findFor(new ExternalIdentity("https://unknown.example/", subject)).isEmpty());
        assertEquals(beforeCompanies, jdbc.queryForList("select * from companies order by id"));
        assertEquals(beforeGrants, jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id"));
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.tables where table_schema = 'public'
                and table_name in ('subscriptions', 'payments', 'users', 'invitations')
                """, Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "missing-company", "null-issuer", "blank-issuer",
            "null-subject", "empty-subject", "blank-subject", "unicode-issuer", "unicode-subject"})
    void rejectedGrantPreservesAllExistingRows(String invalid) {
        Company company = company("Constraint fixture");
        var identity = new ExternalIdentity("https://issuer.example/", UUID.randomUUID().toString());
        grant(identity, company.id());
        UUID companyId = invalid.equals("missing-company") ? UUID.randomUUID() : company.id();
        String issuer = switch (invalid) {
            case "null-issuer" -> null;
            case "blank-issuer" -> " \t ";
            case "unicode-issuer" -> "\u2003";
            default -> identity.issuer();
        };
        String subject = switch (invalid) {
            case "null-subject" -> null;
            case "empty-subject" -> "";
            case "blank-subject" -> " \t\n ";
            case "unicode-subject" -> "\u2003";
            default -> identity.subject();
        };
        var beforeCompanies = jdbc.queryForList("select * from companies order by id");
        var beforeGrants = jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into company_memberships(issuer, subject, company_id) values (?, ?, ?)",
                issuer, subject, companyId));
        assertEquals(beforeCompanies, jdbc.queryForList("select * from companies order by id"));
        assertEquals(beforeGrants, jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id"));
    }

    @Test
    void databaseAndDomainRejectEveryJavaWhitespaceCodePointWithoutChangingRows() {
        Company company = company("Whitespace constraint fixture");
        var beforeCompanies = jdbc.queryForList("select * from companies order by id");
        var beforeGrants = jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id");
        int[] whitespace = java.util.stream.IntStream.rangeClosed(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
                .filter(Character::isWhitespace).toArray();
        for (int point : whitespace) {
            String blank = new String(Character.toChars(point));
            for (int component = 0; component < 2; component++) {
                String issuer = component == 0 ? blank : "https://whitespace.example/";
                String subject = component == 1 ? blank : "subject";
                assertThrows(IllegalArgumentException.class, () -> new ExternalIdentity(issuer, subject));
                assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                        "insert into company_memberships(issuer, subject, company_id) values (?, ?, ?)",
                        issuer, subject, company.id()), "Code point " + point + ", component " + component);
                assertEquals(beforeCompanies, jdbc.queryForList("select * from companies order by id"));
                assertEquals(beforeGrants, jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id"));
            }
        }
    }

    @Test
    void upgradingV1PreservesCompaniesWithoutGrantingAccess() {
        String schema = "membership_upgrade";
        var old = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas(schema).defaultSchema(schema).target("1").cleanDisabled(true).load();
        old.migrate();
        var upgradeJdbc = new JdbcTemplate(new DriverManagerDataSource(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()));
        upgradeJdbc.update("insert into membership_upgrade.companies(id, legal_name, tax_identifier) values (?, ?, ?)",
                UUID.randomUUID(), "Existing company", "12345");
        var before = upgradeJdbc.queryForList("select * from membership_upgrade.companies order by id");
        var latest = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas(schema).defaultSchema(schema).cleanDisabled(true).load();

        assertEquals(1, latest.migrate().migrationsExecuted);
        assertEquals(before, upgradeJdbc.queryForList("select * from membership_upgrade.companies order by id"));
        assertEquals(0, upgradeJdbc.queryForObject("select count(*) from membership_upgrade.company_memberships", Integer.class));
        assertEquals(0, latest.migrate().migrationsExecuted);
        assertEquals(before, upgradeJdbc.queryForList("select * from membership_upgrade.companies order by id"));
    }

    @Test
    void preservesLiteralIdentityUuidOrderingAndRowsAfterRejectedInsert() throws Exception {
        String token = UUID.randomUUID().toString();
        var exact = new ExternalIdentity(" https://issuer.example/" + token + " ", " User-' OR '1'='1-" + token + " ");
        var trimmed = new ExternalIdentity(exact.issuer().trim(), exact.subject().trim());
        var lower = new ExternalIdentity(exact.issuer(), exact.subject().toLowerCase(java.util.Locale.ROOT));
        String name = "  O'Reilly \uD83C\uDF0D " + token + "  ";
        var first = new Company(UUID.fromString("00000000-0000-0000-0000-000000000001"), name, " \u00A0tax' ");
        var second = new Company(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), name, null);
        companies.add(second);
        companies.add(first);
        grant(exact, second.id());
        grant(exact, first.id());
        byte[] before = storedRowsDigest();

        assertEquals(List.of(first, second), authorizedCompanies.findFor(exact));
        assertTrue(authorizedCompanies.findFor(trimmed).isEmpty());
        assertTrue(authorizedCompanies.findFor(lower).isEmpty());
        assertEquals(first, companies.findById(first.id()).orElseThrow());
        assertTrue(companies.findById(UUID.randomUUID()).isEmpty());
        assertArrayEquals(before, storedRowsDigest());
        assertThrows(DataIntegrityViolationException.class,
                () -> companies.add(new Company(first.id(), "replacement", "changed")));
        assertArrayEquals(before, storedRowsDigest());
        assertEquals(List.of(first, second), authorizedCompanies.findFor(exact));
        var failure = assertThrows(NullPointerException.class, () -> authorizedCompanies.findFor(null));
        assertEquals("External identity is required", failure.getMessage());
        assertArrayEquals(before, storedRowsDigest());
    }

    @Test
    void preservesPoolBounds() {
        var pool = (com.zaxxer.hikari.HikariDataSource) jdbc.getDataSource();
        assertEquals(5, pool.getMaximumPoolSize());
        assertEquals(1, pool.getMinimumIdle());
    }

    private byte[] storedRowsDigest() throws Exception {
        String rows = jdbc.queryForList("select * from companies order by id").toString()
                + jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id");
        return java.security.MessageDigest.getInstance("SHA-256")
                .digest(rows.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private Company company(String name) {
        var company = new Company(UUID.randomUUID(), name, null);
        companies.add(company);
        return company;
    }

    private void grant(ExternalIdentity identity, UUID companyId) {
        jdbc.update("insert into company_memberships(issuer, subject, company_id) values (?, ?, ?)",
                identity.issuer(), identity.subject(), companyId);
    }
}
