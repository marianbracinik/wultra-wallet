package com.wultra.wallet.data;

import com.wultra.wallet.exception.RateException;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Value
public class RateSnapshot {

    Map<String, Rate> rates; // [currencyCode: Rate]
    LocalDate rateDate;
    Instant fetchedAt;
    @With
    boolean freshSnapshot;

    public RateSnapshot(Map<String, Rate> rates, LocalDate rateDate, Instant fetchedAt) {
        this(rates, rateDate, fetchedAt, false);
    }

    public RateSnapshot(Map<String, Rate> rates, LocalDate rateDate, Instant fetchedAt, boolean freshSnapshot) {
        this.rates = Map.copyOf(rates);
        this.rateDate = rateDate;
        this.fetchedAt = fetchedAt;
        this.freshSnapshot = freshSnapshot;
    }

    public Rate getRate(String currencyCode) throws RateException {
        Rate result = rates.get(currencyCode);
        if (result == null) {
            throw new RateException("Current rate not found for the currency " + currencyCode + ".");
        }
        return result;
    }

}
