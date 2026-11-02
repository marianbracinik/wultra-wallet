package com.wultra.wallet.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AmountException extends WalletException {

    public AmountException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", message);
    }

}
