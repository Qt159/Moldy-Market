package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    @Query("select distinct o from Order o left join fetch o.orderItems where o.id in :ids")
    List<Order> findAllByIdInWithItems(@Param("ids") Collection<UUID> ids);

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

    @Query(value = """
            select o.id from Order o
            where o.storeId = :storeId
              and (:status is null or o.status = :status)
              and (:fromInstant is null or o.createdAt >= :fromInstant)
              and (:toInstant is null or o.createdAt < :toInstant)
              and (:keywordPattern is null
                   or lower(o.orderCode) like :keywordPattern
                   or lower(o.receiverName) like :keywordPattern
                   or exists (
                       select 1 from OrderItem i
                       where i.order = o and lower(i.productName) like :keywordPattern
                   ))
            """, countQuery = """
            select count(o) from Order o
            where o.storeId = :storeId
              and (:status is null or o.status = :status)
              and (:fromInstant is null or o.createdAt >= :fromInstant)
              and (:toInstant is null or o.createdAt < :toInstant)
              and (:keywordPattern is null
                   or lower(o.orderCode) like :keywordPattern
                   or lower(o.receiverName) like :keywordPattern
                   or exists (
                       select 1 from OrderItem i
                       where i.order = o and lower(i.productName) like :keywordPattern
                   ))
            """)
    Page<UUID> findStoreOrderIdsFiltered(@Param("storeId") UUID storeId,
                                         @Param("status") OrderStatus status,
                                         @Param("keywordPattern") String keywordPattern,
                                         @Param("fromInstant") Instant fromInstant,
                                         @Param("toInstant") Instant toInstant,
                                         Pageable pageable);

    @Query("""
            select distinct o from Order o
            left join fetch o.checkoutGroup
            left join fetch o.orderItems
            where o.id in :ids
            """)
    List<Order> findAllByIdInWithItemsAndCheckoutGroup(@Param("ids") Collection<UUID> ids);

    @Query("""
            select o.status, count(o)
            from Order o
            where o.storeId = :storeId
            group by o.status
            """)
    List<Object[]> countStoreOrdersByStatus(@Param("storeId") UUID storeId);

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

    long countByBuyerIdAndStatusIn(UUID buyerId, Collection<OrderStatus> statuses);
    long countBySellerIdAndStatusIn(UUID sellerId, Collection<OrderStatus> statuses);
}
