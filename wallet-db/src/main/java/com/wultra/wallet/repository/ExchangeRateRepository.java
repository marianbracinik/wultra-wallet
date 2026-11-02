package com.wultra.wallet.repository;

import com.wultra.wallet.domain.ExchangeRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists individual currency exchange rates belonging to the stored rate snapshot.
 */
public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, String> {
}
