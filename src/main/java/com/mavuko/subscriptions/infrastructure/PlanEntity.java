package com.mavuko.subscriptions.infrastructure;

import java.math.BigDecimal;
import java.time.Period;
import java.util.OptionalInt;

import com.mavuko.subscriptions.domain.Plan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "subscription_plans")
public class PlanEntity {
    @Id
    @Column(columnDefinition = "text")
    private String code;

    @Column(name = "display_name", nullable = false, columnDefinition = "text")
    private String displayName;

    @Column(name = "price_cop", nullable = false, columnDefinition = "numeric")
    private BigDecimal priceCop;

    @Column(name = "duration_amount", nullable = false)
    private int durationAmount;

    @Column(name = "duration_unit", nullable = false, columnDefinition = "text")
    private String durationUnit;

    @Column(name = "user_limit")
    private Integer userLimit;

    @Column(name = "location_limit", nullable = false)
    private int locationLimit;

    protected PlanEntity() {
    }

    Plan toDomain() {
        Period period = switch (durationUnit) {
            case "DAYS" -> Period.ofDays(durationAmount);
            case "MONTHS" -> Period.ofMonths(durationAmount);
            default -> throw new IllegalStateException("Unsupported plan duration unit: " + durationUnit);
        };
        return new Plan(displayName, priceCop, period,
                userLimit == null ? OptionalInt.empty() : OptionalInt.of(userLimit), locationLimit);
    }
}
