package com.wultra.wallet.rest;

import lombok.Value;

import java.math.BigDecimal;

/**
 * Wallet balance in a single currency returned after a deposit or withdrawal.
 */
@Value
public class BalanceResponse {
    String currency;
    BigDecimal amount;
}
