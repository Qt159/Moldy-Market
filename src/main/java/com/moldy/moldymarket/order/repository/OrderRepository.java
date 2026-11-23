package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findAllByBuyerId(@Param("buyerId") UUID buyerId);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.moldy.moldymarket.order.enums.OrderStatus.DISPUTED,
               o.updatedAt = :now
         where o.id = :id
           and o.status = com.moldy.moldymarket.order.enums.OrderStatus.COMPLETED
        """)
    int markDisputed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.moldy.moldymarket.order.enums.OrderStatus.COMPLETED,
               o.updatedAt = :now
         where o.id = :id
           and o.status = com.moldy.moldymarket.order.enums.OrderStatus.DISPUTED
        """)
    int revertDisputed(@Param("id") UUID id, @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByCheckoutGroupIdAndStatusIn(UUID checkoutGroupId, Collection<OrderStatus> statuses);

    @Query("""
        select o.id from Order o
        where o.status = com.moldy.moldymarket.order.enums.OrderStatus.COMPLETED
        and o.settledAt is null
        and o.returnDeadline < :now
        order by o.returnDeadline asc
""")
    List<UUID> findDueForSettlementIds(@Param("now") Instant now);
}
