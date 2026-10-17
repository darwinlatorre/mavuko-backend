package com.mavuko.subscriptions.infrastructure;

import java.math.BigDecimal;
import java.time.Period;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import com.mavuko.subscriptions.application.PlanCatalogService;
import com.mavuko.subscriptions.domain.Plan;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.dao.DataIntegrityViolationException;
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
class PlanCatalogPersistenceIT {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("mavuko.database.url", DATABASE::getJdbcUrl);
        properties.add("mavuko.database.username", DATABASE::getUsername);
        properties.add("mavuko.database.password", DATABASE::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlanCatalogService catalog;

    @Test
    void seedsDocumentedTermsInDatabase() {
        var rows = jdbc.queryForList("select * from subscription_plans order by code");
        assertEquals(List.of("business", "growth", "trial"),
                rows.stream().map(row -> row.get("code")).toList());
        var trial = rows.get(2);
        assertEquals("Prueba", trial.get("display_name"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) trial.get("price_cop")));
        assertEquals(15, trial.get("duration_amount"));
        assertEquals("DAYS", trial.get("duration_unit"));
        assertEquals(2, trial.get("user_limit"));
        assertEquals(1, trial.get("location_limit"));
        assertEquals("Negocio", rows.get(0).get("display_name"));
        assertEquals(new BigDecimal("39900"), rows.get(0).get("price_cop"));
        assertEquals("Crecimiento", rows.get(1).get("display_name"));
        assertEquals(new BigDecimal("69900"), rows.get(1).get("price_cop"));
        for (int index = 0; index < 2; index++) {
            assertEquals(1, rows.get(index).get("duration_amount"));
            assertEquals("MONTHS", rows.get(index).get("duration_unit"));
            assertNull(rows.get(index).get("user_limit"));
        }
        assertEquals(3, rows.get(0).get("location_limit"));
        assertEquals(5, rows.get(1).get("location_limit"));
        assertEquals(new Plan("Prueba", BigDecimal.ZERO, Period.ofDays(15), OptionalInt.of(2), 1),
                catalog.findByCode("trial").orElseThrow());
        assertEquals(new Plan("Negocio", new BigDecimal("39900"), Period.ofMonths(1), OptionalInt.empty(), 3),
                catalog.findByCode("business").orElseThrow());
        assertEquals(new Plan("Crecimiento", new BigDecimal("69900"), Period.ofMonths(1), OptionalInt.empty(), 5),
                catalog.findByCode("growth").orElseThrow());
    }

    @Test
    void readsAddedAndEditedTermsWithoutRestartOrRounding() {
        Plan originalBusiness = catalog.findByCode("business").orElseThrow();
        jdbc.update("""
                insert into subscription_plans values ('custom', 'Custom', 12345.6701, 7, 'DAYS', 4, 2)
                """);
        try {
            assertEquals(new Plan("Custom", new BigDecimal("12345.6701"), Period.ofDays(7), OptionalInt.of(4), 2),
                    catalog.findByCode("custom").orElseThrow());
            jdbc.update("""
                    update subscription_plans set display_name = 'Updated', price_cop = 54321.9876,
                    duration_amount = 2, duration_unit = 'MONTHS', user_limit = null, location_limit = 9
                    where code = 'business'
                    """);
            Plan updated = new Plan("Updated", new BigDecimal("54321.9876"),
                    Period.ofMonths(2), OptionalInt.empty(), 9);
            var beforeReads = jdbc.queryForList("select * from subscription_plans order by code");
            assertEquals(updated, catalog.findByCode("business").orElseThrow());
            assertEquals(List.of("Updated", "Custom", "Crecimiento", "Prueba"),
                    catalog.findAll().stream().map(Plan::displayName).toList());
            assertTrue(catalog.findByCode("unknown").isEmpty());
            assertTrue(catalog.findByCode("' OR '1'='1").isEmpty());
            assertTrue(catalog.findByCode("BUSINESS").isEmpty());
            assertEquals(beforeReads, jdbc.queryForList("select * from subscription_plans order by code"));
        } finally {
            jdbc.update("delete from subscription_plans where code = 'custom'");
            jdbc.update("""
                    update subscription_plans set display_name = ?, price_cop = ?, duration_amount = 1,
                    duration_unit = 'MONTHS', user_limit = null, location_limit = ? where code = 'business'
                    """, originalBusiness.displayName(), originalBusiness.priceCop(), originalBusiness.locationLimit());
        }
        assertEquals(originalBusiness, catalog.findByCode("business").orElseThrow());
        assertTrue(catalog.findByCode("custom").isEmpty());
    }

    @Test
    void invalidEditsLeaveTheCatalogUnchanged() {
        var before = jdbc.queryForList("select * from subscription_plans order by code");
        for (String edit : List.of("code = 'business'", "price_cop = -1", "price_cop = 'NaN'", "price_cop = 'Infinity'",
                "price_cop = null", "display_name = U&'\\2003'", "duration_amount = 0",
                "duration_unit = 'YEARS'", "user_limit = 0", "location_limit = -1")) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "update subscription_plans set " + edit + " where code = 'trial'"), edit);
            assertEquals(before, jdbc.queryForList("select * from subscription_plans order by code"));
        }
    }

    @Test
    void upgradingV2PreservesCompanyAccessAndDoesNotReseedEdits() {
        String schema = "catalog_upgrade";
        var old = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas(schema).defaultSchema(schema).target("2").cleanDisabled(true).load();
        old.migrate();
        var upgrade = new JdbcTemplate(new DriverManagerDataSource(
                DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword()));
        UUID companyId = UUID.randomUUID();
        upgrade.update("insert into catalog_upgrade.companies values (?, ?, ?)", companyId, "Preserved", "123");
        upgrade.update("insert into catalog_upgrade.company_memberships values (?, ?, ?)",
                "https://issuer.example/", "owner", companyId);
        var companies = upgrade.queryForList("select * from catalog_upgrade.companies order by id");
        var grants = upgrade.queryForList("select * from catalog_upgrade.company_memberships order by subject");
        var latest = Flyway.configure().dataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword())
                .schemas(schema).defaultSchema(schema).cleanDisabled(true).load();
        assertEquals(1, latest.migrate().migrationsExecuted);
        assertEquals(companies, upgrade.queryForList("select * from catalog_upgrade.companies order by id"));
        assertEquals(grants, upgrade.queryForList("select * from catalog_upgrade.company_memberships order by subject"));
        assertEquals(3, upgrade.queryForObject("select count(*) from catalog_upgrade.subscription_plans", Integer.class));
        upgrade.update("update catalog_upgrade.subscription_plans set price_cop = 123.45 where code = 'business'");
        var edited = upgrade.queryForList("select * from catalog_upgrade.subscription_plans order by code");
        assertEquals(0, latest.migrate().migrationsExecuted);
        assertEquals(edited, upgrade.queryForList("select * from catalog_upgrade.subscription_plans order by code"));
    }
}
