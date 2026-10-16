package com.mavuko.companies.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.mavuko.companies.domain.Company;
import com.mavuko.companies.domain.CompanyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("persistence")
public class JpaCompanyRepository implements CompanyRepository {
    private final CompanyJpaRepository repository;

    public JpaCompanyRepository(CompanyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(Company company) {
        repository.saveAndFlush(CompanyMapper.toEntity(company));
    }

    @Override
    public Optional<Company> findById(UUID companyId) {
        return repository.findById(companyId).map(CompanyMapper::toDomain);
    }
}
