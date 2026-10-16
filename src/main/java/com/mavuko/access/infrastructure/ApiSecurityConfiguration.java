package com.mavuko.access.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.OAuth2ProtectedResourceMetadataFilter;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Auth0Settings.class)
public class ApiSecurityConfiguration {
    @Bean
    RestTemplate auth0JwkClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return new RestTemplate(factory);
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, Auth0Settings settings, RestTemplate auth0JwkClient)
            throws Exception {
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint()))
                // Authentication alone never grants company or global-administrator access.
                .authorizeHttpRequests(access -> access.anyRequest().denyAll());

        if (settings.configured()) {
            var decoder = NimbusJwtDecoder.withJwkSetUri(settings.issuer() + ".well-known/jwks.json")
                    .jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(auth0JwkClient).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(settings.issuer()),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                            values -> values != null && values.contains(settings.audience())),
                    new JwtClaimValidator<Instant>(JwtClaimNames.EXP, value -> value != null),
                    new JwtClaimValidator<String>(JwtClaimNames.SUB, value -> value != null && !value.isBlank())));
            http.oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.decoder(decoder)));
        }
        var chain = http.build();
        // Spring's metadata filter responds before authorization; this unit exposes no public endpoint.
        return new DefaultSecurityFilterChain(chain.getRequestMatcher(), chain.getFilters().stream()
                .filter(filter -> !(filter instanceof OAuth2ProtectedResourceMetadataFilter)).toList());
    }
}
