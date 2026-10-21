package com.moldy.moldymarket.pricing.repository;

import com.moldy.moldymarket.pricing.entity.PricingMode;
import com.moldy.moldymarket.pricing.entity.PricingRequestEntity;
import com.moldy.moldymarket.pricing.entity.PricingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PricingRequestRepository extends JpaRepository<PricingRequestEntity, UUID> {
    List<PricingRequestEntity> findBySellerId(UUID sellerId);
    
    List<PricingRequestEntity> findByAppraiserIdAndStatus(UUID appraiserId, PricingStatus status);
    
    List<PricingRequestEntity> findByListingId(UUID listingId);
    
    long countBySellerId(UUID sellerId);
}
