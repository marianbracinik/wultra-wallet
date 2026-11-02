package com.wultra.wallet.controller;

import com.wultra.wallet.exception.WalletException;
import com.wultra.wallet.rest.BalanceResponse;
import com.wultra.wallet.rest.MoneyRequest;
import com.wultra.wallet.rest.TotalResponse;
import com.wultra.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {
    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/deposits")
    public BalanceResponse deposit(Principal principal, @Valid @RequestBody MoneyRequest request) throws WalletException {
        return service.createDeposit(principal.getName(), request.getCurrency(), request.getAmount());
    }

    @PostMapping("/withdrawals")
    public BalanceResponse withdraw(Principal principal, @Valid @RequestBody MoneyRequest request) throws WalletException {
        return service.withdraw(principal.getName(), request.getCurrency(), request.getAmount());
    }

    @GetMapping("/balances")
    public Map<String, BigDecimal> getBalances(Principal principal) {
        return service.getBalances(principal.getName());
    }

    @DeleteMapping("/balances")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearAll(Principal principal) throws WalletException {
        service.clearAll(principal.getName());
    }

    @GetMapping("/total")
    public TotalResponse getTotal(Principal principal, @RequestParam String currency) throws WalletException {
        return service.getTotal(principal.getName(), currency);
    }

}
