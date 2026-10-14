package com.mavuko.companies.infrastructure;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyPersistenceConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CompanyPersistenceConfiguration.class);

    @Test
    void defaultProfileDoesNotCreateDatabaseConnections() {
        context.run(application -> {
            assertThat(application).hasNotFailed();
            assertThat(application).doesNotHaveBean(DataSource.class);
        });
    }

    @Test
    void persistenceProfileRejectsEmptyPasswordBeforeOpeningAConnection() {
        context.withInitializer(application -> application.getEnvironment()
                        .setActiveProfiles("persistence"))
                .withPropertyValues("mavuko.database.url=jdbc:postgresql://127.0.0.1:1/not-used",
                        "mavuko.database.username=test", "mavuko.database.password=")
                .run(application -> {
                    assertThat(application).hasFailed();
                    assertThat(application.getStartupFailure()).hasRootCauseMessage(
                            "Database URL, username and password are required");
                });
    }
}
