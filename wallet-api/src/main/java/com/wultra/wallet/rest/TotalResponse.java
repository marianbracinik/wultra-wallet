package com.wultra.wallet.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Value
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TotalResponse {
    String currency;
    BigDecimal amount;
    boolean rateExchangeUsed;
    LocalDate rateDate;
    Instant ratesFetchedAt;
    Boolean freshRateSnapshot;
}
