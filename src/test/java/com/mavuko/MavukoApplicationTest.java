package com.mavuko;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MavukoApplicationTest {
    @Autowired ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(context.containsBean("entityManagerFactory")).isFalse();
        assertThat(context.containsBean("companyJpaRepository")).isFalse();
        assertThat(context.containsBean("companyService")).isFalse();
    }
}
