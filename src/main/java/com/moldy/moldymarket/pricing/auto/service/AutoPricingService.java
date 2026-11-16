
package com.moldy.moldymarket.pricing.auto.service;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.pricing.auto.dto.AutoPricingRequest;
import com.moldy.moldymarket.pricing.auto.dto.AutoPricingResponse;
import com.moldy.moldymarket.pricing.entity.PricingRequestEntity;
import com.moldy.moldymarket.pricing.entity.ReferencePriceEntity;
import com.moldy.moldymarket.pricing.auto.helper.DepreciationFactorHelper;
import com.moldy.moldymarket.pricing.auto.helper.PricingCalculationHelper;
import com.moldy.moldymarket.pricing.auto.mapper.AutoPricingMapper;
import com.moldy.moldymarket.pricing.repository.PricingRequestRepository;
import com.moldy.moldymarket.pricing.reference.repository.ReferencePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoPricingService {

    private static final String SUPPORTED_ORIGIN = "OFFICIAL_VN";
    private static final String DEFAULT_DISCLAIMER =
            "Giá tham khảo — không phải giá thẩm định chính thức";
    private static final String PHONE_CATEGORY_NAME = "Điện thoại";
    private static final String LAPTOP_CATEGORY_NAME = "Laptop";

    private final ReferencePriceRepository referencePriceRepository;
    private final PricingRequestRepository pricingRequestRepository;
    private final CategoryRepository categoryRepository;
    private final AutoPricingMapper autoPricingMapper;

    /*
      Tính giá tự động nếu sản phẩm đáp ứng điều kiện.
      Nếu không đáp ứng, lưu yêu cầu với trạng thái MANUAL_REQUIRED.
     */
    @Transactional
    public AutoPricingResponse calculatePrice(
            UUID sellerId, AutoPricingRequest request) {

        AutoPricingValidationResult validationResult = validateAutoPricingEligibility(request);

        if (!validationResult.isValid()) {
            PricingRequestEntity pricingRequest = new PricingRequestEntity(
                    sellerId,
                    request.categoryId(),
                    request.modelId(),
                    request.origin(),
                    request.warrantyStatus(),
                    request.conditionLevel(),
                    request.usageMonths());

            pricingRequest.setManualRequired(validationResult.getReason());
            pricingRequestRepository.save(pricingRequest);

            return autoPricingMapper.toManualRequiredResponse(validationResult.getReason());
        }

        ReferencePriceEntity referencePrice = referencePriceRepository.findById(request.modelId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Model not found: " + request.modelId()));

        BigDecimal depreciationFactor = DepreciationFactorHelper.calculateFactor(request.usageMonths());

        BigDecimal conditionFactor = request.conditionLevel().getFactor();
        BigDecimal warrantyFactor = request.warrantyStatus().getFactor();

        long suggestedPrice = PricingCalculationHelper.calculateSuggestedPrice(
                referencePrice.getReferencePrice(), depreciationFactor,
                conditionFactor, warrantyFactor);

        PricingRequestEntity pricingRequest = new PricingRequestEntity(
                sellerId,
                request.categoryId(),
                request.modelId(),
                request.origin(),
                request.warrantyStatus(),
                request.conditionLevel(),
                request.usageMonths()
        );

        pricingRequest.setAutoPricingResult(
                BigDecimal.valueOf(suggestedPrice),
                referencePrice.getReferencePrice(),
                depreciationFactor,
                conditionFactor,
                warrantyFactor,
                referencePrice.getModelName(),
                referencePrice.getUpdatedAt(),
                DEFAULT_DISCLAIMER
        );

        pricingRequestRepository.save(pricingRequest);

        return autoPricingMapper.toSuggestedResponse(
                suggestedPrice,
                referencePrice.getReferencePrice(),
                depreciationFactor,
                conditionFactor,
                warrantyFactor,
                referencePrice.getModelName(),
                referencePrice.getUpdatedAt(),
                DEFAULT_DISCLAIMER
        );
    }

    private AutoPricingValidationResult validateAutoPricingEligibility(
            AutoPricingRequest request) {

        if (!SUPPORTED_ORIGIN.equals(request.origin())) {
            return new AutoPricingValidationResult(false, "ORIGIN_NOT_SUPPORTED");}

        if (!isSupportedCategory(request.categoryId())) {
            return new AutoPricingValidationResult(false, "CATEGORY_NOT_SUPPORTED");}

        if (!referencePriceRepository.existsById(request.modelId())) {
            return new AutoPricingValidationResult(false, "MODEL_NOT_FOUND");}
        return new AutoPricingValidationResult(true, null);
    }

    private boolean isSupportedCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElse(null);
        if (category == null) {
            return false;
        }
        String categoryName = category.getName();

        return PHONE_CATEGORY_NAME.equals(categoryName) || LAPTOP_CATEGORY_NAME.equals(categoryName);
    }

    private static class AutoPricingValidationResult {

        private final boolean valid;
        private final String reason;

        AutoPricingValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        boolean isValid() {
            return valid;
        }

        String getReason() {
            return reason;
        }
    }
}
