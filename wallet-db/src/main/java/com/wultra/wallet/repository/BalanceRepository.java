package com.wultra.wallet.repository;

import com.wultra.wallet.domain.BalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Persists currency balances and provides wallet owner queries and bulk deletion.
 */
public interface BalanceRepository extends JpaRepository<BalanceEntity, BalanceEntity.Id> {
    /**
     * Finds all currency balances belonging to a wallet owner.
     *
     * @param owner wallet owner
     * @return matching balances, or an empty list
     */
    List<BalanceEntity> findAllByIdWalletOwner(String owner);

    /**
     * Deletes all currency balances belonging to a wallet owner.
     *
     * @param owner wallet owner
     */
    @Modifying
    @Query("delete from BalanceEntity balance where balance.id.wallet.owner = :owner")
    void deleteAllByOwner(String owner);
}
