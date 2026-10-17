package com.mavuko.subscriptions.domain;

import java.math.BigDecimal;
import java.time.Period;
import java.util.OptionalInt;

/** Current offer terms. An absent user limit means commercially unlimited users. */
public record Plan(
        String displayName,
        BigDecimal priceCop,
        Period period,
        OptionalInt userLimit,
        int locationLimit) {
}
