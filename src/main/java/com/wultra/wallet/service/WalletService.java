package com.wultra.wallet.service;

import com.wultra.wallet.error.WalletException;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;

@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private final WalletPersistenceService repository;
    private final RateService rates;

    public WalletService(WalletPersistenceService repository, RateService rates) {
        this.repository = repository;
        this.rates = rates;
    }

    public Balance createDeposit(String owner, String code, BigDecimal amount) {
        Currency currency = currency(code);
        validateAmount(amount, currency);
        if (!"CZK".equals(code) && !rates.current().getSnapshot().getRates().containsKey(code)) {
            throw unsupportedCurrency();
        }
        log.trace("Depositing money into the authenticated user's wallet (currency: {}).", code);
        return new Balance(code, repository.change(owner, code, amount));
    }

    public Balance withdraw(String owner, String code, BigDecimal amount) {
        Currency currency = currency(code);
        validateAmount(amount, currency);
        // allow withdrawals from existing holdings even when CNB is unavailable
        if (!"CZK".equals(code) && !repository.getBalances(owner).containsKey(code)
                && !rates.current().getSnapshot().getRates().containsKey(code)) {
            throw unsupportedCurrency();
        }
        log.trace("Withdrawing money from the authenticated user's wallet (currency: {}).", code);
        return new Balance(code, repository.change(owner, code, amount.negate()));
    }

    public Map<String, BigDecimal> balances(String owner) { return repository.getBalances(owner); }

    public Total total(String owner, String code) {
        Currency target = currency(code);
        Map<String, BigDecimal> balances = repository.getBalances(owner);
        boolean conversion = balances.entrySet().stream()
                .anyMatch(entry -> entry.getValue().signum() != 0 && !entry.getKey().equals(code));
        RateService.Rates selected = null;
        if (conversion || (!"CZK".equals(code) && !balances.containsKey(code))) {
            selected = rates.current();
            if (!selected.getSnapshot().getRates().containsKey(code)) {
                throw unsupportedCurrency();
            }
        }

        // value all holdings against a single immutable rate snapshot
        log.trace("Calculating the authenticated user's total balance (currency: {}, conversion: {}).", code, conversion);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : balances.entrySet()) {
            BigDecimal amount = entry.getValue();
            if (amount.signum() == 0) {
                continue;
            }
            if (!entry.getKey().equals(code)) {
                RateSnapshot.Rate source = rate(selected.getSnapshot(), entry.getKey());
                RateSnapshot.Rate destination = rate(selected.getSnapshot(), code);
                BigDecimal numerator = amount.multiply(source.getCzkRate()).multiply(BigDecimal.valueOf(destination.getUnits()));
                BigDecimal denominator = destination.getCzkRate().multiply(BigDecimal.valueOf(source.getUnits()));
                amount = numerator.divide(denominator, MathContext.DECIMAL128);
            }
            total = total.add(amount);
        }
        return new Total(code, total.setScale(fractionDigits(target), RoundingMode.HALF_EVEN),
                conversion ? selected.getSnapshot().getRateDate() : null,
                conversion ? selected.getSnapshot().getFetchedAt() : null, conversion && selected.isFallback());
    }

    private RateSnapshot.Rate rate(RateSnapshot snapshot, String code) {
        RateSnapshot.Rate rate = snapshot.getRates().get(code);
        if (rate == null) {
            throw new WalletException(HttpStatus.SERVICE_UNAVAILABLE, "RATES_UNAVAILABLE", "A holding has no available exchange rate.");
        }
        return rate;
    }

    private Currency currency(String code) {
        try {
            if (code == null || !code.matches("[A-Z]{3}")) {
                throw unsupportedCurrency();
            }
            return Currency.getInstance(code);
        } catch (IllegalArgumentException exception) {
            throw unsupportedCurrency();
        }
    }

    private void validateAmount(BigDecimal amount, Currency currency) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > fractionDigits(currency)
                || amount.compareTo(new BigDecimal("1E26")) >= 0) {
            throw new WalletException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "Provide a positive amount within the currency precision and balance limit.");
        }
    }

    private int fractionDigits(Currency currency) {
        // XDR has no ISO minor unit; retain four decimal places for this CNB unit
        return currency.getDefaultFractionDigits() < 0 ? 4 : currency.getDefaultFractionDigits();
    }

    private WalletException unsupportedCurrency() {
        return new WalletException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY", "The currency is invalid or not supported by CNB.");
    }

    @Value
    public static class Balance {
        String currency;
        BigDecimal amount;
    }

    @Value
    public static class Total {
        String currency;
        BigDecimal amount;
        LocalDate rateDate;
        Instant ratesFetchedAt;
        boolean fallback;
    }
}
