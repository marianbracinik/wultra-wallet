package com.wultra.wallet.data;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class Rate {

    int units;
    BigDecimal czkRate;

    public static final Rate RATE_CZK = new Rate(1, BigDecimal.ONE);

}
