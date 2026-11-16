package com.moldy.moldymarket.pricing.reference.repository;

import com.moldy.moldymarket.pricing.entity.ReferencePriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferencePriceRepository extends JpaRepository<ReferencePriceEntity, UUID> {
    Optional<ReferencePriceEntity> findByBrandAndModelName(String brand, String modelName);
    
    boolean existsByBrandAndModelName(String brand, String modelName);
}
