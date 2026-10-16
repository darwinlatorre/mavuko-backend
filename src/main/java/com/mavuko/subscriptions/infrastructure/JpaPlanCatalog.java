package com.mavuko.subscriptions.infrastructure;

import java.util.List;
import java.util.Optional;

import com.mavuko.subscriptions.domain.Plan;
import com.mavuko.subscriptions.domain.PlanCatalog;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("persistence")
public class JpaPlanCatalog implements PlanCatalog {
    private final PlanJpaRepository repository;

    public JpaPlanCatalog(PlanJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Plan> findAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(PlanEntity::toDomain).toList();
    }

    @Override
    public Optional<Plan> findByCode(String code) {
        return repository.findById(code).map(PlanEntity::toDomain);
    }
}
