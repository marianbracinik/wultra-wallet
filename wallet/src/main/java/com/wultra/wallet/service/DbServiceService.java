package com.wultra.wallet.service;

import com.wultra.wallet.data.Rate;
import com.wultra.wallet.data.RateSnapshot;
import com.wultra.wallet.domain.BalanceEntity;
import com.wultra.wallet.domain.ExchangeRateEntity;
import com.wultra.wallet.domain.RateSnapshotEntity;
import com.wultra.wallet.domain.WalletEntity;
import com.wultra.wallet.exception.AmountException;
import com.wultra.wallet.exception.InsufficientFundsException;
import com.wultra.wallet.exception.WalletException;
import com.wultra.wallet.exception.WalletNotFoundException;
import com.wultra.wallet.repository.BalanceRepository;
import com.wultra.wallet.repository.ExchangeRateRepository;
import com.wultra.wallet.repository.RateSnapshotRepository;
import com.wultra.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DbServiceService {

    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999999999999.9999");

    private final WalletRepository walletRepository;
    private final BalanceRepository balanceRepository;
    private final RateSnapshotRepository rateSnapshotRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    @Transactional(rollbackFor = WalletException.class)
    public BigDecimal updateCurrencyBalance(String owner, String currency, BigDecimal delta) throws WalletException {
        // find the wallet
        WalletEntity wallet = walletRepository.findByOwner(owner)
                .orElseThrow(WalletNotFoundException::new);

        // create the balance
        BalanceEntity.Id id = new BalanceEntity.Id(wallet, currency);
        BalanceEntity balance = balanceRepository.findById(id).orElse(null);
        BigDecimal oldBalance = balance == null ? BigDecimal.ZERO : balance.getAmount();
        BigDecimal newBalance = oldBalance.add(delta);

        // check new balance
        if (newBalance.signum() < 0) {
            throw new InsufficientFundsException();
        }
        if (newBalance.compareTo(MAX_BALANCE) > 0) {
            throw new AmountException("Amount is out of balance limit.");
        }

        // save the balance
        if (balance == null) {
            balanceRepository.save(new BalanceEntity(id, newBalance));
        } else {
            balance.setAmount(newBalance);
        }

        log.trace("DB balance updated ({}, {}, {}).", owner, currency, delta);
        return newBalance;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getBalances(String owner) {
        Map<String, BigDecimal> balances = new TreeMap<>();
        for (BalanceEntity balance : this.balanceRepository.findAllByIdWalletOwner(owner)) {
            balances.put(balance.getId().getCurrency(), balance.getAmount());
        }
        return balances;
    }

    @Transactional(rollbackFor = WalletException.class)
    public void clearAll(String owner) throws WalletException {
        walletRepository.findByOwner(owner).orElseThrow(WalletNotFoundException::new);
        balanceRepository.deleteAllByOwner(owner);
    }

    @Transactional(readOnly = true)
    public Optional<RateSnapshot> loadRateSnapshot() {
        RateSnapshotEntity snapshot = rateSnapshotRepository.findWithRatesById(1).orElse(null);
        if (snapshot == null) {
            return Optional.empty();
        }
        Map<String, Rate> rates = new HashMap<>();
        for (ExchangeRateEntity rate : snapshot.getRates()) {
            rates.put(rate.getCurrency(), new Rate(rate.getUnits(), rate.getCzkRate()));
        }
        return Optional.of(new RateSnapshot(rates, snapshot.getRateDate(), snapshot.getFetchedAt()));
    }

    @Transactional
    public void saveRateSnapshot(RateSnapshot snapshot) {
        RateSnapshotEntity storedSnapshot = rateSnapshotRepository.findWithRatesById(1).orElse(null);
        if (storedSnapshot == null) {
            storedSnapshot = rateSnapshotRepository.saveAndFlush(new RateSnapshotEntity(1, snapshot.getRateDate(), snapshot.getFetchedAt()));
        } else {
            storedSnapshot.setRateDate(snapshot.getRateDate());
            storedSnapshot.setFetchedAt(snapshot.getFetchedAt());
        }
        Map<String, ExchangeRateEntity> oldRates = new HashMap<>();
        for (ExchangeRateEntity rate : storedSnapshot.getRates()) {
            oldRates.put(rate.getCurrency(), rate);
        }

        // replace the full snapshot in one transaction, including removed currencies
        for (Map.Entry<String, Rate> entry : snapshot.getRates().entrySet()) {
            ExchangeRateEntity currentRate = oldRates.remove(entry.getKey());
            if (currentRate == null) {
                ExchangeRateEntity addedRate = exchangeRateRepository.save(new ExchangeRateEntity(
                        entry.getKey(),
                        storedSnapshot,
                        entry.getValue().getUnits(),
                        entry.getValue().getCzkRate()));
                storedSnapshot.getRates().add(addedRate);
            } else {
                currentRate.setUnits(entry.getValue().getUnits());
                currentRate.setCzkRate(entry.getValue().getCzkRate());
            }
        }

        // save exchange rates
        exchangeRateRepository.deleteAll(oldRates.values());
        storedSnapshot.getRates().removeAll(oldRates.values());
        log.info("DB exchange rate updated (rateDate: {}).", snapshot.getRateDate());
    }
}
