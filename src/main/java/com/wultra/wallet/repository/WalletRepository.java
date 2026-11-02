package com.wultra.wallet.repository;

import com.wultra.wallet.domain.WalletEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<WalletEntity, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletEntity> findByOwner(String owner);
}
