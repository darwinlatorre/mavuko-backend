package com.mavuko.companies.infrastructure;

import java.util.List;
import java.util.Objects;

import com.mavuko.access.domain.ExternalIdentity;
import com.mavuko.companies.application.AuthorizedCompanies;
import com.mavuko.companies.domain.Company;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Profile("persistence")
@Transactional(readOnly = true)
public class JpaAuthorizedCompanies implements AuthorizedCompanies {
    private final CompanyJpaRepository repository;

    public JpaAuthorizedCompanies(CompanyJpaRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public List<Company> findFor(ExternalIdentity identity) {
        Objects.requireNonNull(identity, "External identity is required");
        return repository.findGrantedCompanies(identity.issuer(), identity.subject()).stream()
                .map(CompanyMapper::toDomain).toList();
    }
}
