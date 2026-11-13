
package com.moldy.moldymarket.appraiser.helper;

import com.moldy.moldymarket.appraiser.entity.Appraiser;
import com.moldy.moldymarket.appraiser.entity.AppraiserCategory;
import com.moldy.moldymarket.appraiser.repository.AppraiserCategoryRepository;
import com.moldy.moldymarket.appraiser.repository.AppraiserRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppraiserHelper {

    private final AppraiserRepository appraiserRepository;
    private final AppraiserCategoryRepository appraiserCategoryRepository;
    private final UserRepository userRepository;

    public User getUser(UUID userId) {
        return userRepository.findById(userId)
        .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_USER_NOT_FOUND));
    }

    public Appraiser getAppraiser(UUID appraiserId) {
        return appraiserRepository.findById(appraiserId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_NOT_FOUND));
    }

    public Appraiser getAppraiserByUserId(UUID userId) {
        return appraiserRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(ErrorCode.APPRAISER_PROFILE_NOT_FOUND));
    }

   private List<UUID> getCategoryIds(List<AppraiserCategory> categories) {
    List<UUID> categoryIds = new java.util.ArrayList<>();
    for (AppraiserCategory category : categories) {
        categoryIds.add(category.getCategoryId());}
    return categoryIds;
    }

    public void saveCategories(UUID appraiserId, List<UUID> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }

        List<AppraiserCategory> categories = new ArrayList<>();
        for (UUID categoryId : categoryIds) {
            categories.add(new AppraiserCategory(appraiserId, categoryId));
        }
        appraiserCategoryRepository.saveAll(categories);
    }

    public void deleteCategories(UUID appraiserId) {
        appraiserCategoryRepository.deleteByAppraiserId(appraiserId);
    }
}
