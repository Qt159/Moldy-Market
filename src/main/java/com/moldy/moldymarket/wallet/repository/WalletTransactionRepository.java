package com.moldy.moldymarket.wallet.repository;

import com.moldy.moldymarket.wallet.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    boolean existsByIdempotencyKey(String idempotencyKey);

    List<WalletTransaction> findAllByWalletIdOrderByCreatedAtDesc(Long walletId);
}
