package com.wultra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Stores a wallet's balance in a single currency, identified by the wallet and currency code.
 */
@Getter
@Entity
@Table(name = "balance")
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BalanceEntity {

    @EmbeddedId
    private Id id;

    @Setter
    @Column(nullable = false, precision = 30, scale = 4)
    private BigDecimal amount;

    /**
     * Composite balance primary key consisting of the owning wallet and currency code.
     */
    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "owner", nullable = false)
        private WalletEntity wallet;

        @Column(length = 3, nullable = false, columnDefinition = "CHAR(3)")
        private String currency;
    }
}
