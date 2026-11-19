package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckoutGroupRepository extends JpaRepository<CheckoutGroup, UUID> {

    @Query("""
            select cg from CheckoutGroup  cg
            where cg.expiredAt <= :now
                  and cg.status = :status
            order by cg.expiredAt
            """)
    List<UUID> findExpiredCheckoutIds(@Param("now") Instant now, @Param("status") CheckoutGroupStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select cg from CheckoutGroup cg where cg.id = :id")
    Optional<CheckoutGroup> findByIdForUpdate(@Param("id") UUID id);

}
