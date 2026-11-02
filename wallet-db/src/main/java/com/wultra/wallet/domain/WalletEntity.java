package com.wultra.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents a wallet identified by its owner.
 */
@Entity
@Table(name = "wallet")
@Getter
@EqualsAndHashCode(of = "owner")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class WalletEntity {

    @Id
    @Column(length = 64)
    private String owner;

}
