package com.wultra.wallet.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.wultra.wallet.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
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

    public WalletController(WalletService service) { this.service = service; }

    @PostMapping("/deposits")
    public WalletService.Balance deposit(Principal principal, @Valid @RequestBody MoneyRequest request) {
        return service.createDeposit(principal.getName(), request.getCurrency(), request.getAmount());
    }

    @PostMapping("/withdrawals")
    public WalletService.Balance withdraw(Principal principal, @Valid @RequestBody MoneyRequest request) {
        return service.withdraw(principal.getName(), request.getCurrency(), request.getAmount());
    }

    @GetMapping("/balances")
    public Map<String, BigDecimal> balances(Principal principal) { return service.balances(principal.getName()); }

    @GetMapping("/total")
    public WalletService.Total total(Principal principal, @RequestParam String currency) {
        return service.total(principal.getName(), currency);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MoneyRequest {
        @NotNull
        @Pattern(regexp = "[A-Z]{3}")
        private String currency;

        @NotNull
        private BigDecimal amount;
    }
}
