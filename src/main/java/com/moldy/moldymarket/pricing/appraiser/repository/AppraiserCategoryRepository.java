package com.moldy.moldymarket.pricing.appraiser.repository;

import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserCategoryEntity;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AppraiserCategoryRepository extends JpaRepository<AppraiserCategoryEntity, AppraiserCategoryId> {
    List<AppraiserCategoryEntity> findByAppraiserId(UUID appraiserId);
    
    List<AppraiserCategoryEntity> findByCategoryId(UUID categoryId);
    
    boolean existsByAppraiserIdAndCategoryId(UUID appraiserId, UUID categoryId);
    
    void deleteByAppraiserId(UUID appraiserId);
}
