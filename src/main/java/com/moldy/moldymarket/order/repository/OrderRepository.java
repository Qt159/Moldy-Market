package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query("select o.checkoutGroup.id from Order o where o.id = :id")
    Optional<UUID> findCheckoutGroupIdByOrderId(@Param("id") UUID id);

    List<Order> findAllByBuyerIdOrderByCreatedAtDesc(@Param("buyerId") UUID buyerId);

    @Query(value = """
            select distinct o from Order o
            left join fetch o.checkoutGroup
            left join fetch o.orderItems
            where o.storeId = :storeId and (:status is null or o.status = :status)
            """, countQuery = """
            select count(o) from Order o
            where o.storeId = :storeId and (:status is null or o.status = :status)
            """)
    Page<Order> findStoreOrders(@Param("storeId") UUID storeId,
                                @Param("status") OrderStatus status,
                                Pageable pageable);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.moldy.moldymarket.order.enums.OrderStatus.DISPUTED,
               o.updatedAt = :now, o.version = o.version + 1
         where o.id = :id
           and o.status = com.moldy.moldymarket.order.enums.OrderStatus.COMPLETED
        """)
    int markDisputed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.moldy.moldymarket.order.enums.OrderStatus.COMPLETED,
               o.updatedAt = :now, o.version = o.version + 1
         where o.id = :id
           and o.status = com.moldy.moldymarket.order.enums.OrderStatus.DISPUTED
        """)
    int revertDisputed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.moldy.moldymarket.order.enums.OrderStatus.REFUNDED,
               o.updatedAt = :now, o.version = o.version + 1
         where o.id = :id
           and o.status = com.moldy.moldymarket.order.enums.OrderStatus.DISPUTED
        """)
    int markRefunded(@Param("id") UUID id, @Param("now") Instant now);
}
