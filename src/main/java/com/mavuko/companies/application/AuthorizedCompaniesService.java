package com.mavuko.companies.application;

import java.util.List;

import com.mavuko.access.domain.ExternalIdentity;
import com.mavuko.companies.domain.Company;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists explicit memberships; does not grant operation-specific permission. */
@Service
@Profile("persistence")
public class AuthorizedCompaniesService {
    private final AuthorizedCompanies companies;

    public AuthorizedCompaniesService(AuthorizedCompanies companies) {
        this.companies = companies;
    }

    @Transactional(readOnly = true)
    public List<Company> findFor(ExternalIdentity identity) {
        return companies.findFor(identity);
    }
}
