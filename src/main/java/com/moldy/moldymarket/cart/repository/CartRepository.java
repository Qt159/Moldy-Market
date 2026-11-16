package com.moldy.moldymarket.cart.repository;

import com.moldy.moldymarket.cart.entity.Cart;
import com.moldy.moldymarket.cart.entity.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    
    /* Lấy giỏ hàng ACTIVE của một user
       Mỗi user chỉ có tối đa 1 giỏ hàng ACTIVE
     */
    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);

    // Lấy giỏ hàng ACTIVE của user
    @Query("SELECT c FROM Cart c " +
           "WHERE c.user.id = :userId " +
           "AND c.status = 'ACTIVE'")
    Optional<Cart> findActiveCartByUserId(@Param("userId") UUID userId);

    // Lấy giỏ hàng kèm items (fetch join để tối ưu N+1 problem)
    @Query("SELECT c FROM Cart c " +
           "LEFT JOIN FETCH c.items " +
           "WHERE c.id = :cartId")
    Optional<Cart> findByIdWithItems(@Param("cartId") UUID cartId);

    // Lấy giỏ hàng ACTIVE kèm items của user
    @Query("SELECT c FROM Cart c " +
           "LEFT JOIN FETCH c.items " +
           "WHERE c.user.id = :userId " +
           "AND c.status = 'ACTIVE'")
    Optional<Cart> findActiveCartByUserIdWithItems(@Param("userId") UUID userId);

    // Dùng để cleanup trong batch job
    @Query("SELECT c FROM Cart c " +
           "WHERE c.status = 'ACTIVE' " +
           "AND c.lastUpdatedAt <= :thirtyDaysAgo")
    List<Cart> findAbandonedCarts(@Param("thirtyDaysAgo") Instant thirtyDaysAgo);

  
    boolean existsByUserIdAndStatus(UUID userId, CartStatus status);
    
    @Query("SELECT SUM(c.totalItems) FROM Cart c " +
           "WHERE c.user.id = :userId " +
           "AND c.status = 'ACTIVE'")
    Integer countItemsInActiveCart(@Param("userId") UUID userId);
}
