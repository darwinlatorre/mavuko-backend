package com.mavuko.companies.application;

import java.util.List;
import java.util.UUID;

import com.mavuko.access.domain.ExternalIdentity;
import com.mavuko.companies.domain.Company;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthorizedCompaniesServiceTransactionTest {
    @Test
    void membershipLookupRunsInAnApplicationReadOnlyTransaction() {
        new ApplicationContextRunner()
                .withUserConfiguration(CompanyServiceTransactionTest.TransactionConfiguration.class,
                        AuthorizedCompaniesService.class)
                .withBean(AuthorizedCompanies.class, () -> mock(AuthorizedCompanies.class))
                .withInitializer(application -> application.getEnvironment().setActiveProfiles("persistence"))
                .run(application -> {
                    var identity = new ExternalIdentity("https://issuer.example/", "subject");
                    var expected = List.of(new Company(UUID.randomUUID(), "Company", null));
                    when(application.getBean(AuthorizedCompanies.class).findFor(identity)).thenAnswer(invocation -> {
                        CompanyServiceTransactionTest.assertTransaction(true);
                        return expected;
                    });

                    assertEquals(expected, application.getBean(AuthorizedCompaniesService.class).findFor(identity));
                    verify(application.getBean(AuthorizedCompanies.class)).findFor(identity);
                    assertEquals(1, application.getBean(
                            CompanyServiceTransactionTest.RecordingTransactionManager.class).commits);
                });
    }
}
