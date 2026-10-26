package com.moldy.moldymarket.pricing.appraiser.repository;

import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserProfileEntity;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppraiserProfileRepository extends JpaRepository<AppraiserProfileEntity, UUID> {
    Optional<AppraiserProfileEntity> findByUserId(UUID userId);
    
    List<AppraiserProfileEntity> findByStatus(AppraiserStatus status);

    Page<AppraiserProfileEntity> findByStatus(AppraiserStatus status, Pageable pageable);
    
    boolean existsByUserId(UUID userId);
}
