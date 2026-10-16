package com.mavuko.subscriptions.domain;

import java.util.List;
import java.util.Optional;

/** Current commercial offers; not the conditions of an activated subscription. */
public interface PlanCatalog {
    List<Plan> findAll();

    Optional<Plan> findByCode(String code);
}
