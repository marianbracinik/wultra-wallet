package com.wultra.wallet.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class InsufficientFundsException extends WalletException {

    public InsufficientFundsException() {
        super(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Insufficient funds in the requested currency.");
    }
}
