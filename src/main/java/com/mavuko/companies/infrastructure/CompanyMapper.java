package com.mavuko.companies.infrastructure;

import com.mavuko.companies.domain.Company;

final class CompanyMapper {
    private CompanyMapper() {
    }

    static CompanyEntity toEntity(Company company) {
        return new CompanyEntity(company.id(), company.legalName(), company.taxIdentifier());
    }

    static Company toDomain(CompanyEntity entity) {
        return new Company(entity.getId(), entity.getLegalName(), entity.getTaxIdentifier());
    }
}
