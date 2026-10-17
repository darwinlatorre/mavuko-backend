package com.mavuko.companies.infrastructure;

import java.util.List;
import java.util.UUID;

import com.mavuko.access.domain.ExternalIdentity;
import com.mavuko.companies.application.AuthorizedCompaniesService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("persistence")
public class CompanyController {
    private static final Logger LOG = LoggerFactory.getLogger(CompanyController.class);
    private final AuthorizedCompaniesService companies;

    public CompanyController(AuthorizedCompaniesService companies) {
        this.companies = companies;
    }

    @GetMapping("/api/v1/companies")
    public ResponseEntity<List<CompanySummary>> list(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        if (!request.getParameterMap().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        var identity = new ExternalIdentity(jwt.getClaimAsString(JwtClaimNames.ISS), jwt.getSubject());
        var result = companies.findFor(identity).stream()
                .map(company -> new CompanySummary(company.id(), company.legalName())).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<Void> unavailable(RuntimeException failure) {
        LOG.warn("Company listing is unavailable", failure);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).build();
    }

    public record CompanySummary(UUID id, String legalName) {
    }
}
