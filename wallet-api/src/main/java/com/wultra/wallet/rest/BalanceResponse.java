package com.wultra.wallet.rest;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class BalanceResponse {
    String currency;
    BigDecimal amount;
}
