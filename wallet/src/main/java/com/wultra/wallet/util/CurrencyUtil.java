package com.wultra.wallet.util;

import com.wultra.wallet.data.Rate;
import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.AmountException;
import com.wultra.wallet.exception.RateException;
import com.wultra.wallet.exception.UnsupportedCurrencyException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Currency;
import java.util.Map;

/**
 * Provides currency lookup, amount validation, fraction digit handling, and balance
 * conversion using exchange rates quoted in CZK.
 */
@Slf4j
@UtilityClass
public final class CurrencyUtil {

    public static final String CURRENCY_CODE_CZK = "CZK";

    /**
     * Resolves an ISO currency code.
     *
     * @param code ISO currency code
     * @return matching currency
     * @throws UnsupportedCurrencyException if the code is null or invalid
     */
    public Currency getCurrency(String code) throws UnsupportedCurrencyException {
        try {
            return Currency.getInstance(code);
        } catch (Exception exception) {
            throw new UnsupportedCurrencyException();
        }
    }

    /**
     * Sums balances in the target currency using CZK cross rates
     *
     * @param balances amounts by currency code
     * @param targetCurrencyCode target currency code
     * @param rateSnapshot conversion rates; may be null when no nonzero balance needs conversion
     * @return total before rounding to the target currency fraction digits
     * @throws RateException if a required rate is missing
     */
    public BigDecimal getTotal(Map<String, BigDecimal> balances, String targetCurrencyCode, RateSnapshot rateSnapshot) throws RateException {
        BigDecimal result = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> balanceEntry : balances.entrySet()) {
            BigDecimal amount = balanceEntry.getValue();
            if (amount.signum() == 0) {
                continue;
            }

            if (!balanceEntry.getKey().equals(targetCurrencyCode)) {
                amount = getAmount(amount, balanceEntry.getKey(), targetCurrencyCode, rateSnapshot);
            }
            result = result.add(amount);
        }
        return result;
    }

    /**
     * Converts a balance amount to the required currency using CZK cross rates and quoted units.
     */
    private BigDecimal getAmount(BigDecimal balanceAmount, String balanceCurrencyCode, String targetCurrencyCode, RateSnapshot rateSnapshot) throws RateException {
        Rate sourceRate = rateSnapshot.getRate(balanceCurrencyCode);
        Rate targetRate = rateSnapshot.getRate(targetCurrencyCode);
        // target amount = source amount x (source CZK rate / source units) / (target CZK rate / target units)
        BigDecimal numerator = balanceAmount.multiply(sourceRate.getCzkRate()).multiply(BigDecimal.valueOf(targetRate.getUnits()));
        BigDecimal denominator = targetRate.getCzkRate().multiply(BigDecimal.valueOf(sourceRate.getUnits()));
        return numerator.divide(denominator, MathContext.DECIMAL128);
    }

    /**
     * Checks that an amount is positive, below 1E26, and within the currency fraction digits.
     *
     * @param amount amount to validate
     * @param currency currency defining the allowed fraction digits
     * @throws AmountException if the amount is null or violates a constraint
     */
    public void validateAmount(BigDecimal amount, Currency currency) throws AmountException {
        if (ObjectUtils.isEmpty(amount) || amount.signum() <= 0) {
            throw new AmountException("Invalid amount. Empty or negative value.");
        }

        // check faction digits
        int amountFractionDigits = amount.stripTrailingZeros().scale();
        int currencyFractionDigits = fractionDigits(currency);
        if (amountFractionDigits > currencyFractionDigits) {
            log.info("Invalid amount. Unexpected fraction digits (amountFraction: {}, currencyFraction: {}).", amountFractionDigits, currencyFractionDigits);
            throw new AmountException("Invalid amount. Unexpected fraction digits.");
        }

        // check max amount
        if (amount.compareTo(new BigDecimal("1E26")) >= 0) {
            throw new AmountException("Invalid amount. The amount is too large (max amount is '1E26')");
        }
    }

    /**
     * Returns the currency fraction digits, using four when no ISO minor unit is defined.
     *
     * @param currency currency to inspect
     * @return number of supported fraction digits
     */
    public int fractionDigits(Currency currency) {
        // XDR has no ISO minor unit => use 4 digits
        return currency.getDefaultFractionDigits() < 0 ? 4 : currency.getDefaultFractionDigits();
    }

}
