package com.wultra.wallet.repository;

import com.wultra.wallet.domain.RateSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * Persists exchange rate snapshot metadata and supports loading a snapshot with its rates.
 */
public interface RateSnapshotRepository extends JpaRepository<RateSnapshotEntity, Integer> {
    /**
     * Loads a snapshot together with its exchange rates.
     *
     * @param id snapshot identifier
     * @return matching snapshot with initialized rates, or an empty optional
     */
    @Query("select snapshot from RateSnapshotEntity snapshot" +
            " left join fetch snapshot.rates" +
            " where snapshot.id = :id")
    Optional<RateSnapshotEntity> findWithRatesById(Integer id);
}
