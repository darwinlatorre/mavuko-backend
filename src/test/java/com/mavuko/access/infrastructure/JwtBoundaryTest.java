package com.mavuko.access.infrastructure;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "mavuko.auth0.issuer=https://tenant.example/", "mavuko.auth0.audience=https://api.example"})
class JwtBoundaryTest {
    private static final String ISSUER = "https://tenant.example/";
    private static final String AUDIENCE = "https://api.example";
    private static final RSAKey KEY = newKey();

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private RestTemplate auth0JwkClient;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        MockRestServiceServer.bindTo(auth0JwkClient).build()
                .expect(manyTimes(), requestTo(ISSUER + ".well-known/jwks.json"))
                .andRespond(withSuccess(new JWKSet(KEY.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
    }

    @Test
    void verifiedIdentityStillCannotAccessBusinessOrAdministration() throws Exception {
        for (var path : List.of("/api/v1/companies", "/api/v1/admin/subscriptions", "/unknown")) {
            var result = mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token("valid")))
                    .andExpect(status().isForbidden()).andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
            assertThat(result.getResponse().getContentAsString()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"signature", "algorithm", "issuer", "audience", "expired", "future",
            "missing-expiration", "missing-issuer", "missing-audience", "missing-subject", "blank-subject"})
    void rejectsInvalidTokensBeforeAuthorization(String variant) throws Exception {
        var result = mvc.perform(get("/api/v1/companies")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(variant)))
                .andExpect(status().isUnauthorized()).andReturn();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
        assertThat(result.getResponse().getContentAsString()).isEmpty();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void acceptsOnlyAuthorizationHeaderBearerTokens() throws Exception {
        for (var request : List.of(
                get("/api/v1/companies"),
                get("/api/v1/companies").header(HttpHeaders.AUTHORIZATION, "Bearer malformed"),
                get("/api/v1/companies").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"),
                get("/api/v1/companies").param("access_token", token("valid")),
                get("/api/v1/companies").cookie(new jakarta.servlet.http.Cookie("access_token", token("valid"))))) {
            var result = mvc.perform(request.accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized()).andReturn();
            assertThat(result.getRequest().getSession(false)).isNull();
        }
    }

    private static String token(String variant) throws Exception {
        var now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject("auth0|local-test")
                .audience(List.of(AUDIENCE, "https://other.example"))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .notBeforeTime(Date.from(now.minusSeconds(10)))
                .claim("permissions", List.of("admin", "read:companies"));
        switch (variant) {
            case "issuer" -> claims.issuer("https://other-tenant.example/");
            case "audience" -> claims.audience("https://management.example/api/v2/");
            case "expired" -> claims.expirationTime(Date.from(now.minusSeconds(300)));
            case "future" -> claims.notBeforeTime(Date.from(now.plusSeconds(300)));
            case "missing-expiration" -> claims.expirationTime(null);
            case "missing-issuer" -> claims.issuer(null);
            case "missing-audience" -> claims.audience((String) null);
            case "missing-subject" -> claims.subject(null);
            case "blank-subject" -> claims.subject(" ");
            default -> { }
        }
        var algorithm = variant.equals("algorithm") ? JWSAlgorithm.RS512 : JWSAlgorithm.RS256;
        var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID(KEY.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(variant.equals("signature") ? newKey() : KEY));
        return jwt.serialize();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("local-test-key").algorithm(JWSAlgorithm.RS256).generate();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("Cannot generate local test key", exception);
        }
    }
}
