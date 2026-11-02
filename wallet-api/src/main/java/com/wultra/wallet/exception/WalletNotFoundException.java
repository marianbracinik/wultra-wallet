package com.wultra.wallet.exception;

import org.springframework.http.HttpStatus;

public class WalletNotFoundException extends WalletException {
    public WalletNotFoundException() {
        super(HttpStatus.NOT_FOUND, "WALLET_NOT_FOUND", "The wallet does not exist.");
    }
}
