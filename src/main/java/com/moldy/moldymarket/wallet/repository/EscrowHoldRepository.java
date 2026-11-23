package com.moldy.moldymarket.wallet.repository;

import com.moldy.moldymarket.wallet.entity.EscrowHold;
import com.moldy.moldymarket.wallet.enums.EscrowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EscrowHoldRepository extends JpaRepository<EscrowHold, Long> {
    Optional<EscrowHold> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EscrowHold e where e.orderId = :orderId")
    Optional<EscrowHold> findByOrderIdForUpdate(@Param("orderId") UUID orderId);



    boolean existsBySellerWalletIdAndStatus(Long sellerWalletId, EscrowStatus status);
}
