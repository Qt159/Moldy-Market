package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
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
           and o.status = com.moldy.moldymarket.order.enums.OrderStatus.DELIVERED
        """)
    int markDisputed(@Param("id") UUID id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Order o
           set o.status = com.moldy.moldymarket.order.enums.OrderStatus.DELIVERED,
               o.updatedAt = :now
         where o.id = :id
           and o.status = com.moldy.moldymarket.order.enums.OrderStatus.DISPUTED
        """)
    int revertDisputed(@Param("id") UUID id, @Param("now") Instant now);
}
