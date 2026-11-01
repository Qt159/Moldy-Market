package com.moldy.moldymarket.cart.repository;

import com.moldy.moldymarket.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    
   
    Optional<CartItem> findByCartIdAndListingId(UUID cartId, UUID listingId);

 
    List<CartItem> findByCartId(UUID cartId);

    
    @Query("SELECT ci FROM CartItem ci " +
           "WHERE ci.cart.id = :cartId " +
           "AND ci.sellerId = :sellerId " +
           "ORDER BY ci.createdAt ASC")
    List<CartItem> findByCartIdAndSellerId(@Param("cartId") UUID cartId, @Param("sellerId") UUID sellerId);

    
    @Query("SELECT DISTINCT ci.sellerId FROM CartItem ci " +
           "WHERE ci.cart.id = :cartId " +
           "ORDER BY ci.createdAt ASC")
    List<UUID> findDistinctSellerIdsByCartId(@Param("cartId") UUID cartId);

    boolean existsByCartIdAndListingId(UUID cartId, UUID listingId);
   
    void deleteByCartIdAndListingId(UUID cartId, UUID listingId);

    void deleteByCartId(UUID cartId);

    int countByCartId(UUID cartId);

    int countByCartIdAndSellerId(UUID cartId, UUID sellerId);
}
