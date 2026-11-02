package com.wultra.wallet.repository;

import com.wultra.wallet.domain.ExchangeRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRateEntity, String> {
}
