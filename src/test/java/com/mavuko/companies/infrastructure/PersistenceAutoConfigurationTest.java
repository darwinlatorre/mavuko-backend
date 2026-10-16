package com.mavuko.companies.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceAutoConfigurationTest {
    @Test
    void missingDatabaseUrlFailsInsteadOfUsingAnImplicitDatabase() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=")
                .run(application -> assertThat(application).hasFailed());
    }
}
