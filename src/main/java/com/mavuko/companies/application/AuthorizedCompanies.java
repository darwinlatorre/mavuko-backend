package com.mavuko.companies.application;

import java.util.List;

import com.mavuko.access.domain.ExternalIdentity;
import com.mavuko.companies.domain.Company;

/** Membership lookup only; operation-specific authorization remains a separate policy. */
public interface AuthorizedCompanies {
    List<Company> findFor(ExternalIdentity identity);
}
