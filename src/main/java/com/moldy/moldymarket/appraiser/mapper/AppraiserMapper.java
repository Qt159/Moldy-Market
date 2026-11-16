
package com.moldy.moldymarket.appraiser.mapper;

import com.moldy.moldymarket.appraiser.dto.response.AppraiserCategoryResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserProfileResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserPublicProfileResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserRegistrationResponse;
import com.moldy.moldymarket.appraiser.dto.response.AppraiserStatusResponse;
import com.moldy.moldymarket.appraiser.entity.Appraiser;
import com.moldy.moldymarket.appraiser.entity.AppraiserCategory;
import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AppraiserMapper {

    public AppraiserProfileResponse toProfileResponse(
            Appraiser appraiser, User user,
            List<AppraiserCategory> appraiserCategories,
            Map<UUID, Category> categoryMap) {

        if (appraiser == null) {
            return null;
        }

        return AppraiserProfileResponse.builder()
                .id(appraiser.getId())
                .userId(appraiser.getUserId())
                .fullName(user != null ? user.getFullName() : null)
                .email(user != null ? user.getEmail() : null)
                .avatarUrl(appraiser.getAvatarUrl())
                .bio(appraiser.getBio())
                .status(appraiser.getStatus())
                .verificationLevel(appraiser.getVerificationLevel())
                .totalCompletedAppraisals(appraiser.getTotalCompletedAppraisals())
                .averageRating(appraiser.getAverageRating())
                .reviewCount(appraiser.getReviewCount())
                .categories(toCategoryResponses(appraiserCategories, categoryMap))
                .createdAt(appraiser.getCreatedAt())
                .updatedAt(appraiser.getUpdatedAt())
                .build();
    }

    
    public AppraiserPublicProfileResponse toPublicProfileResponse(
            Appraiser appraiser, User user,
            List<AppraiserCategory> appraiserCategories,
            Map<UUID, Category> categoryMap) {

        if (appraiser == null) {
            return null;
        }

        return AppraiserPublicProfileResponse.builder()
                .id(appraiser.getId())
                .fullName(user != null ? user.getFullName() : null)
                .avatarUrl(appraiser.getAvatarUrl())
                .bio(appraiser.getBio())
                .status(appraiser.getStatus())
                .verificationLevel(appraiser.getVerificationLevel())
                .totalCompletedAppraisals(appraiser.getTotalCompletedAppraisals())
                .averageRating(appraiser.getAverageRating())
                .reviewCount(appraiser.getReviewCount())
                .categories(toCategoryResponses(appraiserCategories, categoryMap))
                .build();
    }

   
    public AppraiserRegistrationResponse toRegistrationResponse(Appraiser appraiser) {

        if (appraiser == null) {
            return null;
        }
        return AppraiserRegistrationResponse.builder()
                .id(appraiser.getId())
                .status(appraiser.getStatus())
                .createdAt(appraiser.getCreatedAt())
                .build();
    }

   
    public AppraiserStatusResponse toStatusResponse(Appraiser appraiser) {
        if (appraiser == null) {
            return null;
        }
        return AppraiserStatusResponse.builder()
                .id(appraiser.getId())
                .userId(appraiser.getUserId())
                .status(appraiser.getStatus())
                .verificationLevel(appraiser.getVerificationLevel())
                .rejectionReason(appraiser.getRejectionReason())
                .suspensionReason(appraiser.getSuspensionReason())
                .suspensionUntil(appraiser.getSuspensionUntil())
                .updatedAt(appraiser.getUpdatedAt())
                .build();
    }

    private List<AppraiserCategoryResponse> toCategoryResponses(
            List<AppraiserCategory> appraiserCategories,
            Map<UUID, Category> categoryMap) {

        List<AppraiserCategoryResponse> responses = new ArrayList<>();
        if (appraiserCategories == null) {
            return responses;
        }

        for (AppraiserCategory appraiserCategory : appraiserCategories) {
            Category category = null;

            if (categoryMap != null) {
                category = categoryMap.get(appraiserCategory.getCategoryId()); }

            AppraiserCategoryResponse response =
                    AppraiserCategoryResponse.builder()
                            .categoryId(appraiserCategory.getCategoryId())
                            .categoryName(category != null? category.getName(): null)
                            .verified(appraiserCategory.isVerified())
                            .build();
            responses.add(response);
        }
        return responses;
    }
}
