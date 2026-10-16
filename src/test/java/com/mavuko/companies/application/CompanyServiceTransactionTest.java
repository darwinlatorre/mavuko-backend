package com.mavuko.companies.application;

import java.util.Optional;
import java.util.UUID;

import com.mavuko.companies.domain.Company;
import com.mavuko.companies.domain.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyServiceTransactionTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(TransactionConfiguration.class, CompanyService.class)
            .withInitializer(application -> application.getEnvironment().setActiveProfiles("persistence"));

    @Test
    void addStartsAndCommitsAWriteTransactionAroundThePort() {
        context.run(application -> {
            var company = new Company(UUID.randomUUID(), "Company", null);
            var repository = application.getBean(CompanyRepository.class);
            doAnswer(invocation -> {
                assertTransaction(false);
                return null;
            }).when(repository).add(company);

            application.getBean(CompanyService.class).add(company);

            verify(repository).add(company);
            assertEquals(1, application.getBean(RecordingTransactionManager.class).commits);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        });
    }

    @Test
    void lookupStartsAReadOnlyTransactionAroundThePort() {
        context.run(application -> {
            var company = new Company(UUID.randomUUID(), "Company", null);
            when(application.getBean(CompanyRepository.class).findById(company.id())).thenAnswer(invocation -> {
                assertTransaction(true);
                return Optional.of(company);
            });

            assertEquals(Optional.of(company), application.getBean(CompanyService.class).findById(company.id()));
            assertEquals(1, application.getBean(RecordingTransactionManager.class).commits);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        });
    }

    @Test
    void portFailureRollsBackTheApplicationTransaction() {
        context.run(application -> {
            var company = new Company(UUID.randomUUID(), "Company", null);
            doAnswer(invocation -> {
                assertTransaction(false);
                throw new IllegalStateException("port failure");
            }).when(application.getBean(CompanyRepository.class)).add(company);

            var failure = assertThrows(IllegalStateException.class,
                    () -> application.getBean(CompanyService.class).add(company));

            assertEquals("port failure", failure.getMessage());
            var manager = application.getBean(RecordingTransactionManager.class);
            assertEquals(1, manager.rollbacks);
            assertEquals(0, manager.commits);
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        });
    }

    static void assertTransaction(boolean readOnly) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(readOnly, TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TransactionConfiguration {
        @Bean CompanyRepository companies() { return mock(CompanyRepository.class); }
        @Bean RecordingTransactionManager transactionManager() { return new RecordingTransactionManager(); }
    }

    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;

        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
