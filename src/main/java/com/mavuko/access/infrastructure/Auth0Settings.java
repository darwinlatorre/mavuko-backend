package com.mavuko.access.infrastructure;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mavuko.auth0")
public record Auth0Settings(String issuer, String audience) {
    public Auth0Settings {
        issuer = issuer == null ? "" : issuer.strip();
        audience = audience == null ? "" : audience.strip();
        if (issuer.isEmpty() != audience.isEmpty()) {
            throw new IllegalArgumentException("Auth0 issuer and audience must both be configured or both be empty");
        }
        if (!issuer.isEmpty()) {
            var uri = URI.create(issuer);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || !"/".equals(uri.getPath()) || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Auth0 issuer must be an HTTPS origin with a trailing slash");
            }
        }
    }

    public boolean configured() {
        return !issuer.isEmpty();
    }
}
