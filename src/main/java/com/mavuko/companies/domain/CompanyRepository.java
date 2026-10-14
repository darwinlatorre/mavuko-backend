package com.mavuko.companies.domain;

import java.util.Optional;
import java.util.UUID;

/** Internal persistence port, not an authorization boundary or public API. */
public interface CompanyRepository {
    void add(Company company);

    Optional<Company> findById(UUID companyId);
}
