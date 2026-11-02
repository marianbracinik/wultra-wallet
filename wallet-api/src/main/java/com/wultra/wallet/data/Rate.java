package com.wultra.wallet.data;

import lombok.Value;

import java.math.BigDecimal;

/**
 * Exchange rate expressing the CZK value of a quoted number of currency units.
 */
@Value
public class Rate {

    int units;
    BigDecimal czkRate;

    public static final Rate RATE_CZK = new Rate(1, BigDecimal.ONE);

}
