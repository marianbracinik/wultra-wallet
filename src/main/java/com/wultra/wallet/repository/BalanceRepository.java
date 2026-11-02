package com.wultra.wallet.repository;

import com.wultra.wallet.domain.BalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BalanceRepository extends JpaRepository<BalanceEntity, BalanceEntity.Id> {
    List<BalanceEntity> findAllByIdOwner(String owner);
}
