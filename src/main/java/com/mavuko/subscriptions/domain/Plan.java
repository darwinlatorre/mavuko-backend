package com.mavuko.subscriptions.domain;

import java.math.BigDecimal;
import java.time.Period;
import java.util.OptionalInt;

/** Initial catalog terms, not an activated company subscription. */
public record Plan(
        String displayName,
        BigDecimal priceCop,
        Period period,
        OptionalInt userLimit,
        int locationLimit) {
}
