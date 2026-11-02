package com.wultra.wallet.repository;

import com.wultra.wallet.domain.RateSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateRepository extends JpaRepository<RateSnapshotEntity, Integer> {
}
