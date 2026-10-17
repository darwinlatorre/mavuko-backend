package com.mavuko.companies.infrastructure;

import java.util.stream.Stream;

import com.mavuko.companies.application.AuthorizedCompaniesService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CompanyControllerTest {
    @AfterEach
    void clearTestAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @MethodSource("persistenceFailures")
    void persistenceFailureIsAnEmpty503NotAnAuthenticationError(RuntimeException failure) throws Exception {
        var companies = mock(AuthorizedCompaniesService.class);
        when(companies.findFor(any())).thenThrow(failure);
        var jwt = Jwt.withTokenValue("controller-test").header("alg", "RS256")
                .issuer("https://tenant.example/").subject("owner").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var mvc = MockMvcBuilders.standaloneSetup(new CompanyController(companies))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();

        mvc.perform(get("/api/v1/companies")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(""));
    }

    static Stream<RuntimeException> persistenceFailures() {
        return Stream.of(new DataAccessResourceFailureException("Database unavailable"),
                new CannotCreateTransactionException("Database transaction unavailable"));
    }
}
