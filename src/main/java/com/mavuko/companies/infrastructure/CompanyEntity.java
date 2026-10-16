package com.mavuko.companies.infrastructure;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "companies")
public class CompanyEntity implements Persistable<UUID> {
    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false, columnDefinition = "text")
    private String legalName;

    @Column(name = "tax_identifier", columnDefinition = "text")
    private String taxIdentifier;

    @Transient
    private boolean newEntity = true;

    protected CompanyEntity() {
    }

    CompanyEntity(UUID id, String legalName, String taxIdentifier) {
        this.id = id;
        this.legalName = legalName;
        this.taxIdentifier = taxIdentifier;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTaxIdentifier() {
        return taxIdentifier;
    }

    @Override
    public boolean isNew() {
        // Assigned UUIDs must still use INSERT rather than merge existing rows.
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }
}
