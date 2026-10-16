package com.mavuko.companies.application;

import java.util.Optional;
import java.util.UUID;

import com.mavuko.companies.domain.Company;
import com.mavuko.companies.domain.CompanyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Internal company identity operations; not a registration or authorization workflow. */
@Service
@Profile("persistence")
public class CompanyService {
    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    @Transactional
    public void add(Company company) {
        companies.add(company);
    }

    @Transactional(readOnly = true)
    public Optional<Company> findById(UUID companyId) {
        return companies.findById(companyId);
    }
}
