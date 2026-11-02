package com.wultra.wallet.service;

import com.wultra.wallet.error.WalletException;
import com.wultra.wallet.domain.BalanceEntity;
import com.wultra.wallet.repository.BalanceRepository;
import com.wultra.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class WalletPersistenceService {

    private static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999999999999.9999");
    private final WalletRepository wallets;
    private final BalanceRepository balances;

    @Transactional
    public BigDecimal change(String owner, String currency, BigDecimal delta) {
        // serialize changes per wallet, including the first deposit into a currency
        wallets.findByOwner(owner).orElseThrow(() ->
                new WalletException(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "The wallet does not exist."));
        BalanceEntity.Id id = new BalanceEntity.Id(owner, currency);
        BalanceEntity balance = balances.findById(id).orElse(null);
        BigDecimal current = balance == null ? BigDecimal.ZERO : balance.getAmount();
        BigDecimal updated = current.add(delta);
        if (updated.signum() < 0) {
            throw new WalletException(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Insufficient funds in the requested currency.");
        }
        if (updated.compareTo(MAX_BALANCE) > 0) {
            throw new WalletException(HttpStatus.BAD_REQUEST, "BALANCE_LIMIT", "The currency balance limit would be exceeded.");
        }
        if (balance == null) {
            balances.save(new BalanceEntity(id, updated));
        } else {
            balance.setAmount(updated);
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getBalances(String owner) {
        Map<String, BigDecimal> balances = new TreeMap<>();
        for (BalanceEntity balance : this.balances.findAllByIdOwner(owner)) {
            balances.put(balance.getId().getCurrency(), balance.getAmount());
        }
        return balances;
    }
}
