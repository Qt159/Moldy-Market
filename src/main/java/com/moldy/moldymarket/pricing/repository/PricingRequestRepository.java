package com.moldy.moldymarket.pricing.repository;

import com.moldy.moldymarket.pricing.entity.PricingMode;
import com.moldy.moldymarket.pricing.entity.PricingRequestEntity;
import com.moldy.moldymarket.pricing.entity.PricingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PricingRequestRepository extends JpaRepository<PricingRequestEntity, UUID> {
    List<PricingRequestEntity> findBySellerId(UUID sellerId);
    
    List<PricingRequestEntity> findByAppraiserIdAndStatus(UUID appraiserId, PricingStatus status);
    
    List<PricingRequestEntity> findByListingId(UUID listingId);
    
    long countBySellerId(UUID sellerId);

    boolean existsByListingIdAndStatus(UUID listingId, PricingStatus status);

    long countByListingIdAndStatus(UUID listingId, PricingStatus status);

    @Query("SELECT r FROM PricingRequestEntity r WHERE r.pricingMode = :pricingMode " +
           "AND r.status = :status AND (" +
           "(r.appraiserId IS NULL AND r.categoryId IN :categoryIds AND r.sellerId <> :appraiserUserId) OR " +
           "(r.appraiserId = :appraiserUserId)) ORDER BY r.createdAt DESC")
    List<PricingRequestEntity> findAppraiserQueue(
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("appraiserUserId") UUID appraiserUserId,
            @Param("status") PricingStatus status,
            @Param("pricingMode") PricingMode pricingMode
    );
}
