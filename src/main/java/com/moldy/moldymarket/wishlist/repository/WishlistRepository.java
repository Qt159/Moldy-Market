
package com.moldy.moldymarket.wishlist.repository;

import com.moldy.moldymarket.wishlist.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WishlistRepository
        extends JpaRepository<WishlistItem, UUID> {

    Page<WishlistItem> findByUserId(UUID userId, Pageable pageable);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    Optional<WishlistItem> findByUserIdAndProductId(UUID userId, UUID productId);

    long countByUserId(UUID userId);

    long countByProductId(UUID productId);

    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.productId = :productId")
    int deleteAllByProductId(@Param("productId") UUID productId);

    List<WishlistItem> findByProductIdIn(List<UUID> productIds);

    @Modifying
    @Query("""
            DELETE FROM WishlistItem w
            WHERE w.userId = :userId
            AND w.productId = :productId
            """)
    int deleteByUserIdAndProductId(
            @Param("userId") UUID userId,
            @Param("productId") UUID productId);
}
