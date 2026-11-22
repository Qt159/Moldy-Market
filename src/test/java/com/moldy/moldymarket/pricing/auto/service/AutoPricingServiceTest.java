package com.moldy.moldymarket.pricing.auto.service;

import com.moldy.moldymarket.category.entity.Category;
import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.pricing.auto.dto.AutoPricingRequest;
import com.moldy.moldymarket.pricing.auto.dto.AutoPricingResponse;
import com.moldy.moldymarket.pricing.auto.mapper.AutoPricingMapper;
import com.moldy.moldymarket.pricing.entity.*;
import com.moldy.moldymarket.pricing.reference.repository.ReferencePriceRepository;
import com.moldy.moldymarket.pricing.repository.PricingRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoPricingServiceTest {

    @Mock
    private ReferencePriceRepository referencePriceRepository;

    @Mock
    private PricingRequestRepository pricingRequestRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AutoPricingMapper autoPricingMapper;

    @InjectMocks
    private AutoPricingService autoPricingService;

    private UUID sellerId;
    private UUID categoryId;
    private UUID modelId;
    private Category phoneCategory;
    private ReferencePriceEntity referencePriceEntity;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        modelId = UUID.randomUUID();

        phoneCategory = new Category("Điện thoại", "Smartphones");
        ReflectionTestUtils.setField(phoneCategory, "id", categoryId);

        referencePriceEntity = new ReferencePriceEntity(
                categoryId, "Apple", "iPhone 13", BigDecimal.valueOf(18000000), UUID.randomUUID()
        );
        referencePriceEntity.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("should calculate suggested price and save request when all conditions met")
    void should_calculatePrice_when_eligible() {
        // Arrange
        AutoPricingRequest request = new AutoPricingRequest(
                categoryId, modelId, "OFFICIAL_VN", WarrantyStatus.UNDER_WARRANTY, ConditionLevel.NEW, 12
        );

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(phoneCategory));
        when(referencePriceRepository.existsById(modelId)).thenReturn(true);
        when(referencePriceRepository.findById(modelId)).thenReturn(Optional.of(referencePriceEntity));

        AutoPricingResponse expectedResponse = new AutoPricingResponse(
                PricingMode.AUTO, PricingStatus.SUGGESTED, 14000000L, 18000000L,
                BigDecimal.valueOf(0.85), BigDecimal.valueOf(0.95), BigDecimal.valueOf(1.0),
                "iPhone 13", Instant.now(), "disclaimer", null
        );
        when(autoPricingMapper.toSuggestedResponse(anyLong(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedResponse);

        // Act
        AutoPricingResponse response = autoPricingService.calculatePrice(sellerId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.pricingMode()).isEqualTo(PricingMode.AUTO);
        verify(pricingRequestRepository).save(any(PricingRequestEntity.class));
    }

    @Test
    @DisplayName("should return manual required when origin is not OFFICIAL_VN")
    void should_returnManualRequired_when_originNotSupported() {
        // Arrange
        AutoPricingRequest request = new AutoPricingRequest(
                categoryId, modelId, "IMPORT", WarrantyStatus.EXPIRED_OVER_1Y, ConditionLevel.SCRATCHED, 24
        );

        AutoPricingResponse expectedResponse = new AutoPricingResponse(
                PricingMode.AUTO, PricingStatus.MANUAL_REQUIRED, null, null, null, null, null, null, null, null, "ORIGIN_NOT_SUPPORTED"
        );
        when(autoPricingMapper.toManualRequiredResponse("ORIGIN_NOT_SUPPORTED")).thenReturn(expectedResponse);

        // Act
        AutoPricingResponse response = autoPricingService.calculatePrice(sellerId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.reason()).isEqualTo("ORIGIN_NOT_SUPPORTED");
        verify(pricingRequestRepository).save(any(PricingRequestEntity.class));
        verify(referencePriceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("should return manual required when category is not supported")
    void should_returnManualRequired_when_categoryNotSupported() {
        // Arrange
        Category clothingCategory = new Category("Quần áo", "Clothes");
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(clothingCategory));

        AutoPricingRequest request = new AutoPricingRequest(
                categoryId, modelId, "OFFICIAL_VN", WarrantyStatus.EXPIRED_OVER_1Y, ConditionLevel.SCRATCHED, 6
        );

        AutoPricingResponse expectedResponse = new AutoPricingResponse(
                PricingMode.AUTO, PricingStatus.MANUAL_REQUIRED, null, null, null, null, null, null, null, null, "CATEGORY_NOT_SUPPORTED"
        );
        when(autoPricingMapper.toManualRequiredResponse("CATEGORY_NOT_SUPPORTED")).thenReturn(expectedResponse);

        // Act
        AutoPricingResponse response = autoPricingService.calculatePrice(sellerId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.reason()).isEqualTo("CATEGORY_NOT_SUPPORTED");
    }

    @Test
    @DisplayName("should return manual required when model does not exist in reference repository")
    void should_returnManualRequired_when_modelNotFoundInEligibility() {
        // Arrange
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(phoneCategory));
        when(referencePriceRepository.existsById(modelId)).thenReturn(false);

        AutoPricingRequest request = new AutoPricingRequest(
                categoryId, modelId, "OFFICIAL_VN", WarrantyStatus.EXPIRED_OVER_1Y, ConditionLevel.SCRATCHED, 6
        );

        AutoPricingResponse expectedResponse = new AutoPricingResponse(
                PricingMode.AUTO, PricingStatus.MANUAL_REQUIRED, null, null, null, null, null, null, null, null, "MODEL_NOT_FOUND"
        );
        when(autoPricingMapper.toManualRequiredResponse("MODEL_NOT_FOUND")).thenReturn(expectedResponse);

        // Act
        AutoPricingResponse response = autoPricingService.calculatePrice(sellerId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.reason()).isEqualTo("MODEL_NOT_FOUND");
    }
}
