package com.wultra.wallet.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Currency code and amount submitted for a wallet deposit or withdrawal.
 */
@Value
public class MoneyRequest {
    @NotNull
    @Pattern(regexp = "[A-Z]{3}")
    String currency;

    @NotNull
    BigDecimal amount;
}
