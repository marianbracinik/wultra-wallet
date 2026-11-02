package com.wultra.wallet.data;

import com.wultra.wallet.exception.RateException;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Immutable exchange rates indexed by currency code, with their publication date,
 * retrieval timestamp, and freshness status from the latest refresh attempt.
 */
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

    /**
     * Returns the exchange rate for a currency in this snapshot.
     *
     * @param currencyCode currency code to look up
     * @return rate expressed in CZK for its quoted units
     * @throws RateException if the snapshot does not contain the currency
     */
    public Rate getRate(String currencyCode) throws RateException {
        Rate result = rates.get(currencyCode);
        if (result == null) {
            throw new RateException("Current rate not found for the currency " + currencyCode + ".");
        }
        return result;
    }

}
