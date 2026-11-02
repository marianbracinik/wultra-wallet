package com.wultra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Stores the CZK value of a quoted number of currency units within an exchange rate snapshot.
 */
@Entity
@Table(name = "exchange_rate")
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExchangeRateEntity {
    @Id
    @Column(length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private RateSnapshotEntity snapshot;

    @Setter
    @Column(nullable = false)
    private int units;

    @Setter
    @Column(name = "czk_rate", nullable = false, precision = 24, scale = 12)
    private BigDecimal czkRate;
}
