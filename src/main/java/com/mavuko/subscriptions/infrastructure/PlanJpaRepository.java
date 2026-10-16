package com.mavuko.subscriptions.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.repository.Repository;

/** Deliberately read-only: administrative editing is not an exposed application API. */
@Profile("persistence")
public interface PlanJpaRepository extends Repository<PlanEntity, String> {
    List<PlanEntity> findAllByOrderByCodeAsc();

    Optional<PlanEntity> findById(String code);
}
