package com.mavuko.subscriptions.domain;

import java.math.BigDecimal;
import java.time.Period;
import java.util.OptionalInt;

/** An absent user limit means commercially unlimited users. */
public final class PlanCatalog {
    public static final Plan TRIAL = new Plan(
            "Prueba", new BigDecimal("0"), Period.ofDays(15), OptionalInt.of(2), 1);
    public static final Plan BUSINESS = new Plan(
            "Negocio", new BigDecimal("39900"), Period.ofMonths(1), OptionalInt.empty(), 3);
    public static final Plan GROWTH = new Plan(
            "Crecimiento", new BigDecimal("69900"), Period.ofMonths(1), OptionalInt.empty(), 5);

    private PlanCatalog() {
    }
}
