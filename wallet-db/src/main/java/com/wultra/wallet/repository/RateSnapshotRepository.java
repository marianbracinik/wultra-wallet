package com.wultra.wallet.repository;

import com.wultra.wallet.domain.RateSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RateSnapshotRepository extends JpaRepository<RateSnapshotEntity, Integer> {
    @Query("select snapshot from RateSnapshotEntity snapshot" +
            " left join fetch snapshot.rates" +
            " where snapshot.id = :id")
    Optional<RateSnapshotEntity> findWithRatesById(Integer id);
}
