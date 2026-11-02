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


/**
 * Validates wallet operations and coordinates deposits, withdrawals, balance queries,
 * and total valuation using the available exchange rates.
 */
@Slf4j
@Service
public class WalletService {

    private final DbServiceService dbService;
    private final RateService rateService;

    /**
     * Creates the wallet service.
     *
     * @param dbService wallet persistence service
     * @param rateService exchange rate service
     */
    public WalletService(DbServiceService dbService, RateService rateService) {
        this.dbService = dbService;
        this.rateService = rateService;
    }

    /**
     * Validates and deposits an amount in CZK or a currency supported by the available rate snapshot.
     *
     * @param owner wallet owner
     * @param currencyCode ISO currency code
     * @param amount positive amount to deposit
     * @return updated currency balance
     * @throws WalletException if validation fails, required rates are unavailable, the wallet is missing, or the balance limit is exceeded
     */
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

    /**
     * Withdraws a validated amount, allowing existing holdings to be withdrawn without exchange rates.
     *
     * @param owner wallet owner
     * @param currencyCode ISO currency code
     * @param amount positive amount to withdraw
     * @return updated currency balance
     * @throws WalletException if validation fails, required rates are unavailable, the wallet is missing, or funds are insufficient
     */
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

    /**
     * Returns wallet balances ordered by currency code.
     *
     * @param owner wallet owner
     * @return balances by currency, or an empty map if none exist
     */
    public Map<String, BigDecimal> getBalances(String owner) {
        return dbService.getBalances(owner);
    }

    /**
     * Deletes all balances belonging to the wallet.
     *
     * @param owner wallet owner
     * @throws WalletException if the wallet does not exist
     */
    public void clearAll(String owner) throws WalletException {
        dbService.clearAll(owner);
    }

    /**
     * Calculates the total in the requested currency, rounded half-even to its fraction digits.
     * Includes rate metadata when conversion is required.
     *
     * @param owner wallet owner
     * @param currencyCode ISO currency code for the total
     * @return total balance and any conversion metadata
     * @throws WalletException if the currency is unsupported or required rates are unavailable
     */
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
