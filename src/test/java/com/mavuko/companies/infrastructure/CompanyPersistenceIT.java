package com.mavuko.companies.infrastructure;

import java.util.UUID;

import com.mavuko.companies.domain.Company;
import com.mavuko.companies.domain.CompanyRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
class CompanyPersistenceIT {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("mavuko.database.url", DATABASE::getJdbcUrl);
        properties.add("mavuko.database.username", DATABASE::getUsername);
        properties.add("mavuko.database.password", DATABASE::getPassword);
    }

    @Autowired CompanyRepository companies;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired ApplicationContext context;
    @Autowired CompanyJpaRepository entities;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void insertParticipatesInApplicationTransactionRollback() {
        Company company = new Company(UUID.randomUUID(), "Rolled back company", null);
        var before = jdbc.queryForList("select * from companies order by id");

        var failure = assertThrows(IllegalStateException.class,
                () -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    companies.add(company);
                    assertEquals(company, companies.findById(company.id()).orElseThrow());
                    throw new IllegalStateException("application failure");
                }));

        assertEquals("application failure", failure.getMessage());
        assertTrue(companies.findById(company.id()).isEmpty());
        assertEquals(before, jdbc.queryForList("select * from companies order by id"));
    }

    @Test
    void persistenceUsesJpaRepositories() {
        assertTrue(context.containsBean("entityManagerFactory"));
        assertTrue(context.containsBean("companyJpaRepository"));
        assertEquals(JpaCompanyRepository.class, AopUtils.getTargetClass(companies));
        assertTrue(AopUtils.isAopProxy(companies));
    }

    @Test
    void storesCompanyIdentityWithoutCreatingSubscriptionsOrPayments() {
        Company first = new Company(UUID.randomUUID(), "O'Reilly Comercial", "900123456");
        Company second = new Company(UUID.randomUUID(), "Servicios Independientes", null);
        companies.add(first);
        companies.add(second);

        assertEquals(first, companies.findById(first.id()).orElseThrow());
        assertFalse(entities.findById(first.id()).orElseThrow().isNew());
        assertEquals(second, companies.findById(second.id()).orElseThrow());
        assertTrue(companies.findById(UUID.randomUUID()).isEmpty());
        assertEquals(0, jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name in ('subscriptions', 'payments')
                """, Integer.class));
    }

    @Test
    void rejectedDuplicateLeavesAllStoredCompanyDataUnchanged() {
        Company original = new Company(UUID.randomUUID(), "Empresa Original", "12345");
        companies.add(original);
        var before = jdbc.queryForList("select * from companies order by id");

        assertThrows(DataIntegrityViolationException.class, () -> companies.add(
                new Company(original.id(), "Nombre Reemplazado", "99999")));

        assertEquals(before, jdbc.queryForList("select * from companies order by id"));
        assertEquals(original, companies.findById(original.id()).orElseThrow());
    }

    @Test
    void repeatedMigrationPreservesRowsAndAppliesNoAdditionalMigration() {
        Company company = new Company(UUID.randomUUID(), "Empresa Conservada", null);
        companies.add(company);
        var before = jdbc.queryForList("select * from companies order by id");

        assertEquals(0, flyway.migrate().migrationsExecuted);

        assertEquals(before, jdbc.queryForList("select * from companies order by id"));
    }

    @Test
    void databaseRejectsBlankLegalNameWithoutChangingRows() {
        var before = jdbc.queryForList("select * from companies order by id");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into companies(id, legal_name) values (?, ?)", UUID.randomUUID(), " \t "));
        assertEquals(before, jdbc.queryForList("select * from companies order by id"));
    }
}
