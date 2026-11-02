package com.wultra.wallet.repository;

import com.wultra.wallet.domain.BalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BalanceRepository extends JpaRepository<BalanceEntity, BalanceEntity.Id> {
    List<BalanceEntity> findAllByIdWalletOwner(String owner);

    @Modifying
    @Query("delete from BalanceEntity balance where balance.id.wallet.owner = :owner")
    void deleteAllByOwner(String owner);
}
