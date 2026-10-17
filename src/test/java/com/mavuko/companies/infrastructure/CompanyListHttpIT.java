package com.mavuko.companies.infrastructure;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "mavuko.auth0.issuer=https://tenant.example/", "mavuko.auth0.audience=https://api.example"})
@ActiveProfiles("persistence")
@Testcontainers
class CompanyListHttpIT {
    private static final String ISSUER = "https://tenant.example/";
    private static final String AUDIENCE = "https://api.example";
    private static final String PATH = "/api/v1/companies";
    private static final RSAKey KEY = newKey();
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("mavuko.database.url", DATABASE::getJdbcUrl);
        properties.add("mavuko.database.username", DATABASE::getUsername);
        properties.add("mavuko.database.password", DATABASE::getPassword);
    }

    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired RestTemplate auth0JwkClient;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String subject = "Owner-" + UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private List<Map<String, Object>> beforeCompanies;
    private List<Map<String, Object>> beforeMemberships;

    @BeforeEach
    void seedExplicitAccessAndLocalJwks() {
        MockRestServiceServer.bindTo(auth0JwkClient).build()
                .expect(manyTimes(), requestTo(ISSUER + ".well-known/jwks.json"))
                .andRespond(withSuccess(new JWKSet(KEY.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
        UUID hidden = UUID.randomUUID();
        jdbc.update("insert into companies values (?, 'Alpha', 'private-tax-a'), (?, 'Zeta', 'private-tax-z'), (?, 'Hidden', null)",
                first, second, hidden);
        jdbc.update("insert into company_memberships values (?, ?, ?), (?, ?, ?), (?, ?, ?), (?, ?, ?)",
                ISSUER, subject, first, ISSUER, subject, second,
                "https://another-issuer.example/", subject, hidden, ISSUER, subject + "-other", hidden);
        beforeCompanies = jdbc.queryForList("select * from companies order by id");
        beforeMemberships = jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id");
    }

    @AfterEach
    void everyRequestLeavesCompanyAndAccessDataUnchanged() {
        assertEquals(beforeCompanies, jdbc.queryForList("select * from companies order by id"));
        assertEquals(beforeMemberships, jdbc.queryForList("select * from company_memberships order by issuer, subject, company_id"));
    }

    @Test
    void listsOnlyExactIdentityMembershipsAndSelectorFields() throws Exception {
        var response = send("GET", PATH, "Bearer " + token(subject, AUDIENCE));
        assertEquals(200, response.statusCode());
        assertEquals("[{\"id\":\"" + first + "\",\"legalName\":\"Alpha\"},{\"id\":\"" + second
                + "\",\"legalName\":\"Zeta\"}]", response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("application/json"));
        assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
    }

    @Test
    void unknownAndDifferentCaseSubjectsHaveNoImplicitAdminOrEmailAccess() throws Exception {
        for (String caller : List.of("unknown", subject.toLowerCase(java.util.Locale.ROOT), subject + " ")) {
            var response = send("GET", PATH, "Bearer " + token(caller, AUDIENCE));
            assertEquals(200, response.statusCode());
            assertEquals("[]", response.body());
        }
    }

    @Test
    void rejectsMissingMalformedAndWrongAudienceAuthentication() throws Exception {
        for (String authorization : List.of("", "Bearer malformed", "Basic dXNlcjpwYXNz",
                "Bearer " + token(subject, "https://wrong.example"))) {
            var response = send("GET", PATH, authorization);
            assertEquals(401, response.statusCode());
            assertEquals("", response.body());
            assertTrue(response.headers().firstValue("WWW-Authenticate").orElseThrow().startsWith("Bearer"));
        }
    }

    @Test
    void rejectsIdentitySelectorsInsteadOfOverridingTheCaller() throws Exception {
        for (String query : List.of("?subject=another", "?issuer=https://another.example/", "?companyId=" + first,
                "?subject=", "?subject=one&subject=two", "?page=0", "?unused")) {
            var response = send("GET", PATH + query, "Bearer " + token(subject, AUDIENCE));
            assertEquals(400, response.statusCode());
            assertEquals("", response.body());
            everyRequestLeavesCompanyAndAccessDataUnchanged();
        }
    }

    @Test
    void openedGetRejectsInvalidCryptographicIdentity() throws Exception {
        for (String variant : List.of("issuer", "signature", "expired")) {
            var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject).audience(AUDIENCE)
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)));
            if (variant.equals("issuer")) {
                claims.issuer("https://another-issuer.example/");
            }
            if (variant.equals("expired")) {
                claims.expirationTime(Date.from(Instant.now().minusSeconds(300)));
            }
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims.build());
            jwt.sign(new RSASSASigner(variant.equals("signature") ? newKey() : KEY));
            var response = send("GET", PATH, "Bearer " + jwt.serialize());
            assertEquals(401, response.statusCode(), variant);
            assertEquals("", response.body(), variant);
            assertTrue(response.headers().firstValue("WWW-Authenticate").orElseThrow().startsWith("Bearer"));
            everyRequestLeavesCompanyAndAccessDataUnchanged();
        }
    }

    @Test
    void listingDoesNotOpenOtherMethodsOrCompanyOperations() throws Exception {
        String authorization = "Bearer " + token(subject, AUDIENCE);
        for (String method : List.of("POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")) {
            assertEquals(403, send(method, PATH, authorization).statusCode());
        }
        for (String path : List.of(PATH + "/" + first, "/api/v1/admin/subscriptions", "/unknown")) {
            assertEquals(403, send("GET", path, authorization).statusCode());
        }
    }

    @Test
    void unavailableMembershipStoreIsNotReportedAsAnEmptyList() throws Exception {
        jdbc.execute("alter table company_memberships rename to unavailable_memberships");
        try {
            var response = send("GET", PATH, "Bearer " + token(subject, AUDIENCE));
            assertEquals(503, response.statusCode());
            assertEquals("", response.body());
        } finally {
            jdbc.execute("alter table unavailable_memberships rename to company_memberships");
        }
    }

    private HttpResponse<String> send(String method, String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody());
        if (!authorization.isEmpty()) {
            request.header("Authorization", authorization);
        }
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        assertTrue(response.headers().allValues("Location").isEmpty());
        return response;
    }

    private static String token(String subject, String audience) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject).audience(audience)
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("email", "same-email@example.test")
                .claim("permissions", List.of("admin", "read:companies")).build();
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID("company-list-test-key").generate();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("Cannot generate test signing key", exception);
        }
    }
}
