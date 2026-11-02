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

@Slf4j
@UtilityClass
public final class CurrencyUtil {

    public static final String CURRENCY_CODE_CZK = "CZK";

    public Currency getCurrency(String code) throws UnsupportedCurrencyException {
        try {
            return Currency.getInstance(code);
        } catch (Exception exception) {
            throw new UnsupportedCurrencyException();
        }
    }

    public BigDecimal getTotal(Map<String, BigDecimal> balances, String currencyCode, RateSnapshot rateSnapshot) throws RateException {
        BigDecimal result = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> balanceEntry : balances.entrySet()) {
            BigDecimal amount = balanceEntry.getValue();
            if (amount.signum() == 0) {
                continue;
            }

            if (!balanceEntry.getKey().equals(currencyCode)) {
                Rate source = rateSnapshot.getRate(balanceEntry.getKey());
                Rate destination = rateSnapshot.getRate(currencyCode);
                BigDecimal numerator = amount.multiply(source.getCzkRate()).multiply(BigDecimal.valueOf(destination.getUnits()));
                BigDecimal denominator = destination.getCzkRate().multiply(BigDecimal.valueOf(source.getUnits()));
                amount = numerator.divide(denominator, MathContext.DECIMAL128);
            }
            result = result.add(amount);
        }
        return result;
    }

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

    public int fractionDigits(Currency currency) {
        // XDR has no ISO minor unit => use 4 digits
        return currency.getDefaultFractionDigits() < 0 ? 4 : currency.getDefaultFractionDigits();
    }

}
