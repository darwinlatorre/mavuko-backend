package com.mavuko.companies.domain;

import java.util.Objects;
import java.util.UUID;

/** Basic company identity; tax identification may be supplied later. */
public record Company(UUID id, String legalName, String taxIdentifier) {
    public Company {
        Objects.requireNonNull(id, "Company ID is required");
        Objects.requireNonNull(legalName, "Legal name is required");
        if (legalName.isBlank()) {
            throw new IllegalArgumentException("Legal name must not be blank");
        }
    }
}
