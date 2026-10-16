package com.mavuko.companies.infrastructure;

import javax.sql.DataSource;

import com.mavuko.companies.domain.CompanyRepository;
import com.mavuko.companies.application.AuthorizedCompanies;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("persistence")
public class CompanyPersistenceConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource companyDataSource(
            @Value("${mavuko.database.url}") String url,
            @Value("${mavuko.database.username}") String username,
            @Value("${mavuko.database.password}") String password) {
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("Database URL, username and password are required");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
    Flyway companyMigrations(DataSource dataSource) {
        return Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration").cleanDisabled(true).load();
    }

    @Bean
    JdbcTemplate companyJdbc(DataSource dataSource, Flyway companyMigrations) {
        // Resolve the migrated bean before repository access becomes available.
        return new JdbcTemplate(dataSource);
    }

    @Bean
    CompanyRepository companyRepository(JdbcTemplate companyJdbc) {
        return new JdbcCompanyRepository(companyJdbc);
    }

    @Bean
    AuthorizedCompanies authorizedCompanies(JdbcTemplate companyJdbc) {
        return new JdbcAuthorizedCompanies(companyJdbc);
    }
}
