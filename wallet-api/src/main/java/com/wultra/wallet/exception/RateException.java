package com.wultra.wallet.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class RateException extends WalletException {

    public RateException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "RATES_ERROR", message);
    }

}
