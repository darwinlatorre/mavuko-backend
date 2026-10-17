package com.mavuko.access.infrastructure;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.mavuko.MavukoApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"mavuko.auth0.issuer=", "mavuko.auth0.audience="})
class ClosedSecurityTest {
    @Autowired
    private WebApplicationContext context;

    @Test
    void unconfiguredApplicationDeniesRequestsWithoutLoginOrSession() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[] {
                get("/api/v1"), get("/api/v1/companies"), post("/api/v1/companies"), get("/login"), get("/error")}) {
            var result = mvc.perform(request.accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()).andReturn();
            assertThat(result.getResponse().getHeader("Location")).isNull();
            assertThat(result.getRequest().getSession(false)).isNull();
        }
    }

    @Test
    void incompleteOrUnsafeConfigurationFailsStartup() {
        for (var values : new String[][] {
                {"mavuko.auth0.issuer=https://tenant.example/", "mavuko.auth0.audience="},
                {"mavuko.auth0.issuer=", "mavuko.auth0.audience=api"},
                {"mavuko.auth0.issuer=http://tenant.example/", "mavuko.auth0.audience=api"}}) {
            new WebApplicationContextRunner().withUserConfiguration(MavukoApplication.class)
                    .withPropertyValues(values).run(application -> assertThat(application).hasFailed());
        }
    }
}
