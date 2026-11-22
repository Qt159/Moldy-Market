package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Repository
public interface CheckoutGroupRepository extends JpaRepository<CheckoutGroup, UUID> {
    @Query("""
            select c.id from CheckoutGroup c
            where c.status = com.moldy.moldymarket.order.enums.CheckoutGroupStatus.PENDING_PAYMENT
              and c.expiredAt <= :now
            order by c.expiredAt, c.id
            """)
    List<UUID> findExpiredCheckoutIds(@Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CheckoutGroup c where c.id = :id")
    Optional<CheckoutGroup> findByIdForUpdate(@Param("id") UUID id);
}
