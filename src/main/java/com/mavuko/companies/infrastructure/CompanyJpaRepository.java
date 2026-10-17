package com.mavuko.companies.infrastructure;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyJpaRepository extends JpaRepository<CompanyEntity, UUID> {
    @Query(value = """
            select c.id, c.legal_name, c.tax_identifier
            from companies c
            join company_memberships m on m.company_id = c.id
            where m.issuer = :issuer and m.subject = :subject
            order by c.legal_name, c.id
            """, nativeQuery = true)
    List<CompanyEntity> findGrantedCompanies(@Param("issuer") String issuer, @Param("subject") String subject);
}
