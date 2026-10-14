package com.mavuko.companies.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.mavuko.companies.domain.Company;
import com.mavuko.companies.domain.CompanyRepository;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcCompanyRepository implements CompanyRepository {
    private final JdbcTemplate jdbc;

    public JdbcCompanyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void add(Company company) {
        jdbc.update("insert into companies(id, legal_name, tax_identifier) values (?, ?, ?)",
                company.id(), company.legalName(), company.taxIdentifier());
    }

    @Override
    public Optional<Company> findById(UUID companyId) {
        return jdbc.query("select id, legal_name, tax_identifier from companies where id = ?",
                (row, index) -> new Company(row.getObject("id", UUID.class),
                        row.getString("legal_name"), row.getString("tax_identifier")),
                companyId).stream().findFirst();
    }
}
