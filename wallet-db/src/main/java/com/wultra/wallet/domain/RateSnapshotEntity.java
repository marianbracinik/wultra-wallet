package com.wultra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "rate_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RateSnapshotEntity {
    @Id
    private Integer id;

    @Setter
    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Setter
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @OneToMany(mappedBy = "snapshot")
    private List<ExchangeRateEntity> rates = new ArrayList<>();

    public RateSnapshotEntity(Integer id, LocalDate rateDate, Instant fetchedAt) {
        this.id = id;
        this.rateDate = rateDate;
        this.fetchedAt = fetchedAt;
    }
}
