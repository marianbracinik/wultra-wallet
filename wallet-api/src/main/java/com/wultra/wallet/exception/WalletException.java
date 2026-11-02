package com.wultra.wallet.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

@Data
@EqualsAndHashCode(callSuper = true)
public abstract class WalletException extends Exception {

    private final HttpStatus status;
    private final String code;

    protected WalletException(HttpStatus status, String code, String message) {
        this(status, code, message, null);
    }

    protected WalletException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

}
