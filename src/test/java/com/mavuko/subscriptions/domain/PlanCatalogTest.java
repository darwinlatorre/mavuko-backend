package com.mavuko.subscriptions.domain;

import java.math.BigDecimal;
import java.time.Period;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanCatalogTest {
    @Test
    void trialHasDocumentedFreePriceDurationAndLimits() {
        Plan plan = PlanCatalog.TRIAL;
        assertEquals("Prueba", plan.displayName());
        assertEquals(new BigDecimal("0"), plan.priceCop());
        assertEquals(Period.ofDays(15), plan.period());
        assertEquals(OptionalInt.of(2), plan.userLimit());
        assertEquals(1, plan.locationLimit());
    }

    @Test
    void businessHasDocumentedMonthlyPriceAndUnlimitedUsers() {
        Plan plan = PlanCatalog.BUSINESS;
        assertEquals("Negocio", plan.displayName());
        assertEquals(new BigDecimal("39900"), plan.priceCop());
        assertEquals(Period.ofMonths(1), plan.period());
        assertEquals(OptionalInt.empty(), plan.userLimit());
        assertEquals(3, plan.locationLimit());
    }

    @Test
    void growthHasDocumentedMonthlyPriceAndUnlimitedUsers() {
        Plan plan = PlanCatalog.GROWTH;
        assertEquals("Crecimiento", plan.displayName());
        assertEquals(new BigDecimal("69900"), plan.priceCop());
        assertEquals(Period.ofMonths(1), plan.period());
        assertEquals(OptionalInt.empty(), plan.userLimit());
        assertEquals(5, plan.locationLimit());
    }
}
