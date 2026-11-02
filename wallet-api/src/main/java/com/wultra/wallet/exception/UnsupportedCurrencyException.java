package com.wultra.wallet.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedCurrencyException extends WalletException {

    public UnsupportedCurrencyException() {
        super(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY", "The currency is invalid or not supported by CNB.");
    }

}
