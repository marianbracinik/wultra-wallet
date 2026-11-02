package com.wultra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "rate_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RateSnapshotEntity {
    @Id
    private Integer id;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
