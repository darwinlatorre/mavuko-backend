package com.mavuko.companies.infrastructure;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.mavuko.access.domain.ExternalIdentity;
import com.mavuko.companies.application.AuthorizedCompanies;
import com.mavuko.companies.domain.Company;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAuthorizedCompanies implements AuthorizedCompanies {
    private final JdbcTemplate jdbc;

    public JdbcAuthorizedCompanies(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public List<Company> findFor(ExternalIdentity identity) {
        Objects.requireNonNull(identity, "External identity is required");
        return jdbc.query("""
                select c.id, c.legal_name, c.tax_identifier
                from companies c
                join company_memberships m on m.company_id = c.id
                where m.issuer = ? and m.subject = ?
                order by c.legal_name, c.id
                """, (row, number) -> new Company(row.getObject("id", UUID.class),
                row.getString("legal_name"), row.getString("tax_identifier")), identity.issuer(), identity.subject());
    }
}
