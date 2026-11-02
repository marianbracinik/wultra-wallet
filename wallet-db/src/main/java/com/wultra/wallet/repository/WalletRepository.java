package com.wultra.wallet.repository;

import com.wultra.wallet.domain.WalletEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

/**
 * Persists wallets and provides owner lookup with a write lock for balance changes.
 */
public interface WalletRepository extends JpaRepository<WalletEntity, String> {
    /**
     * Finds a wallet and acquires a pessimistic write lock for the current transaction.
     *
     * @param owner wallet owner
     * @return matching wallet, or an empty optional
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletEntity> findByOwner(String owner);
}
