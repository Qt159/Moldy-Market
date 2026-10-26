package com.moldy.moldymarket.pricing.appraiser.mapper;

import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingResponse;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserProfileResponse;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserProfileEntity;
import com.moldy.moldymarket.pricing.entity.PricingRequestEntity;
import com.moldy.moldymarket.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class AppraiserMapper {

    private static final String NEW_APPRAISER_LABEL = "Người định giá mới — chưa đủ dữ liệu đánh giá";

    public AppraiserProfileResponse toProfileResponse(
            AppraiserProfileEntity profile,
            User user,
            List<UUID> categoryIds) {

        if (profile == null) {
            return null;
        }

        String fullName = user != null ? user.getFullName() : null;
        String email = user != null ? user.getEmail() : null;

        String reputationLabel;
        if (profile.getTotalCompleted() != null && profile.getTotalCompleted() >= 10) {
            Double accuracy = profile.getAccuracyPercentage();
            reputationLabel = accuracy != null
                    ? String.format(Locale.US, "%.1f%%", accuracy)
                    : NEW_APPRAISER_LABEL;
        } else {
            reputationLabel = NEW_APPRAISER_LABEL;
        }

        return new AppraiserProfileResponse(
                profile.getId(),
                profile.getUserId(),
                fullName,
                email,
                profile.getStatus(),
                profile.getTotalCompleted(),
                profile.getAccurateCount(),
                profile.getAccuracyPercentage(),
                reputationLabel,
                categoryIds,
                profile.getCreatedAt(),
                profile.getUpdatedAt()
        );
    }

    public AppraiserPricingResponse toPricingResponse(
            PricingRequestEntity entity,
            String appraiserName) {

        if (entity == null) {
            return null;
        }

        return new AppraiserPricingResponse(
                entity.getId(),
                entity.getListingId(),
                entity.getCategoryId(),
                entity.getSellerId(),
                entity.getAppraiserId(),
                appraiserName,
                entity.getPricingMode(),
                entity.getStatus(),
                entity.getSuggestedPrice(),
                entity.getAppraiserNote(),
                entity.getCommissionRate(),
                entity.getRejectCount(),
                entity.getDisclaimer(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().plus(48, ChronoUnit.HOURS) : null
        );
    }
}
