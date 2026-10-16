package com.mavuko.access.domain;

/** Opaque issuer/subject pair; the caller is responsible for authenticating it. */
public record ExternalIdentity(String issuer, String subject) {
    public ExternalIdentity {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("External identity issuer and subject are required");
        }
    }
}
