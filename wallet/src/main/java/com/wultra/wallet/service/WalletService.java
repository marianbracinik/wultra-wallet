package com.wultra.wallet.service;

import com.wultra.wallet.exception.UnsupportedCurrencyException;
import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.exception.WalletException;
import com.wultra.wallet.rest.BalanceResponse;
import com.wultra.wallet.rest.TotalResponse;
import com.wultra.wallet.util.CurrencyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Map;

import static com.wultra.wallet.util.CurrencyUtil.CURRENCY_CODE_CZK;


@Slf4j
@Service
public class WalletService {

    private final DbServiceService dbService;
    private final RateService rateService;

    public WalletService(DbServiceService dbService, RateService rateService) {
        this.dbService = dbService;
        this.rateService = rateService;
    }

    public BalanceResponse createDeposit(String owner, String currencyCode, BigDecimal amount) throws WalletException {
        Currency currency = CurrencyUtil.getCurrency(currencyCode);
        CurrencyUtil.validateAmount(amount, currency);
        if (!CURRENCY_CODE_CZK.equals(currencyCode)
                && !rateService.getRateSnapshot().getRates().containsKey(currencyCode)) {
            throw new UnsupportedCurrencyException();
        }
        BigDecimal newBalance = dbService.updateCurrencyBalance(owner, currencyCode, amount);
        return new BalanceResponse(currencyCode, newBalance);
    }

    public BalanceResponse withdraw(String owner, String currencyCode, BigDecimal amount) throws WalletException {
        Currency currency = CurrencyUtil.getCurrency(currencyCode);
        CurrencyUtil.validateAmount(amount, currency);
        if (!CURRENCY_CODE_CZK.equals(currencyCode)
                && !dbService.getBalances(owner).containsKey(currencyCode) // allow withdrawals from existing holdings even when CNB is unavailable
                && !rateService.getRateSnapshot().getRates().containsKey(currencyCode)) {
            throw new UnsupportedCurrencyException();
        }
        BigDecimal newBalance = dbService.updateCurrencyBalance(owner, currencyCode, amount.negate());
        return new BalanceResponse(currencyCode, newBalance);
    }

    public Map<String, BigDecimal> getBalances(String owner) {
        return dbService.getBalances(owner);
    }

    public void clearAll(String owner) throws WalletException {
        dbService.clearAll(owner);
    }

    public TotalResponse getTotal(String owner, String currencyCode) throws WalletException {
        Currency targetCurrency = CurrencyUtil.getCurrency(currencyCode);
        Map<String, BigDecimal> balances = dbService.getBalances(owner);

        // check if conversion is needed
        boolean conversionNeeded = balances.entrySet().stream()
                .anyMatch(entry -> entry.getValue().signum() != 0 && !entry.getKey().equals(currencyCode));

        // RateSnapshot (if needed)
        RateSnapshot rateSnapshot = null;
        if (conversionNeeded || (!CURRENCY_CODE_CZK.equals(currencyCode) && !balances.containsKey(currencyCode))) {
            rateSnapshot = rateService.getRateSnapshot();
            if (!rateSnapshot.getRates().containsKey(currencyCode)) {
                throw new UnsupportedCurrencyException();
            }
        }

        // calculate the result
        log.trace("Calculating the authenticated user's total balance (currency: {}, conversion: {}).", currencyCode, conversionNeeded);
        BigDecimal result = CurrencyUtil.getTotal(balances, currencyCode, rateSnapshot);

        return new TotalResponse(
                currencyCode,
                result.setScale(CurrencyUtil.fractionDigits(targetCurrency), RoundingMode.HALF_EVEN),
                conversionNeeded,
                conversionNeeded ? rateSnapshot.getRateDate() : null,
                conversionNeeded ? rateSnapshot.getFetchedAt() : null,
                conversionNeeded ? rateSnapshot.isFreshSnapshot() : null);
    }

}
