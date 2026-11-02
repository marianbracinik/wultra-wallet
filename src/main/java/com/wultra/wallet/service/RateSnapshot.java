package com.wultra.wallet.service;

import lombok.Value;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Value
public class RateSnapshot {
    LocalDate rateDate;
    Instant fetchedAt;
    Map<String, Rate> rates;

    public RateSnapshot(LocalDate rateDate, Instant fetchedAt, Map<String, Rate> rates) {
        this.rateDate = rateDate;
        this.fetchedAt = fetchedAt;
        this.rates = Map.copyOf(rates);
    }

    @Value
    public static class Rate {
        int units;
        BigDecimal czkRate;
    }
}
