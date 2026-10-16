package com.mavuko.subscriptions.application;

import java.util.List;
import java.util.Optional;

import com.mavuko.subscriptions.domain.Plan;
import com.mavuko.subscriptions.domain.PlanCatalog;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
@Transactional(readOnly = true)
public class PlanCatalogService {
    private final PlanCatalog catalog;

    public PlanCatalogService(PlanCatalog catalog) {
        this.catalog = catalog;
    }

    public List<Plan> findAll() {
        return catalog.findAll();
    }

    public Optional<Plan> findByCode(String code) {
        return catalog.findByCode(code);
    }
}
