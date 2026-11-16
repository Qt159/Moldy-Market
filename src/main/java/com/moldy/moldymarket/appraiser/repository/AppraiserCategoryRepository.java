
package com.moldy.moldymarket.appraiser.repository;

import com.moldy.moldymarket.appraiser.entity.AppraiserCategory;
import com.moldy.moldymarket.appraiser.entity.AppraiserCategoryId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppraiserCategoryRepository
        extends JpaRepository<AppraiserCategory, AppraiserCategoryId> {

    List<AppraiserCategory> findByAppraiserId(UUID appraiserId);
    List<AppraiserCategory> findByCategoryId(UUID categoryId);

    boolean existsByAppraiserIdAndCategoryId(UUID appraiserId, UUID categoryId);

    void deleteByAppraiserIdAndCategoryId(UUID appraiserId,UUID categoryId);
    void deleteByAppraiserId(UUID appraiserId);
}
