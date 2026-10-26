package com.moldy.moldymarket.pricing.appraiser.service;

import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingCreateRequest;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingResponse;
import com.moldy.moldymarket.pricing.appraiser.dto.AppraiserPricingSubmitRequest;
import com.moldy.moldymarket.pricing.appraiser.entity.AppraiserProfileEntity;
import com.moldy.moldymarket.pricing.appraiser.mapper.AppraiserMapper;
import com.moldy.moldymarket.pricing.appraiser.repository.AppraiserCategoryRepository;
import com.moldy.moldymarket.pricing.appraiser.repository.AppraiserProfileRepository;
import com.moldy.moldymarket.pricing.entity.PricingMode;
import com.moldy.moldymarket.pricing.entity.PricingRequestEntity;
import com.moldy.moldymarket.pricing.entity.PricingStatus;
import com.moldy.moldymarket.pricing.repository.PricingRequestRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppraiserPricingServiceTest {

    @Mock
    private PricingRequestRepository pricingRequestRepository;

    @Mock
    private AppraiserProfileRepository appraiserProfileRepository;

    @Mock
    private AppraiserCategoryRepository appraiserCategoryRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private AppraiserMapper appraiserMapper = new AppraiserMapper();

    @InjectMocks
    private AppraiserPricingService appraiserPricingService;

    private UUID sellerId;
    private UUID appraiserUserId;
    private UUID categoryId;
    private UUID listingId;
    private AppraiserProfileEntity appraiserProfile;
    private User appraiserUser;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        appraiserUserId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        listingId = UUID.randomUUID();

        appraiserProfile = new AppraiserProfileEntity(appraiserUserId);
        org.springframework.test.util.ReflectionTestUtils.setField(appraiserProfile, "id", UUID.randomUUID());
        appraiserProfile.approve();

        appraiserUser = new User();
        appraiserUser.setId(appraiserUserId);
        appraiserUser.setFullName("Chuyen Gia Tran Van B");
        appraiserUser.setEmail("appraiser_b@example.com");
    }

    @Test
    @DisplayName("Tạo yêu cầu định giá chỉ định đích danh người định giá thành công")
    void createRequest_Success_WithSpecificAppraiser() {
        AppraiserPricingCreateRequest request = new AppraiserPricingCreateRequest(
                listingId, categoryId, appraiserProfile.getId(), BigDecimal.valueOf(3.5));

        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(pricingRequestRepository.existsByListingIdAndStatus(listingId, PricingStatus.PENDING)).thenReturn(false);
        when(pricingRequestRepository.countByListingIdAndStatus(listingId, PricingStatus.SELLER_REJECTED)).thenReturn(0L);

        when(appraiserProfileRepository.findById(appraiserProfile.getId())).thenReturn(Optional.of(appraiserProfile));
        when(appraiserCategoryRepository.existsByAppraiserIdAndCategoryId(appraiserProfile.getId(), categoryId)).thenReturn(true);
        when(userRepository.findById(appraiserUserId)).thenReturn(Optional.of(appraiserUser));

        PricingRequestEntity savedEntity = new PricingRequestEntity(
                sellerId, listingId, categoryId, appraiserUserId, BigDecimal.valueOf(3.5));
        when(pricingRequestRepository.save(any(PricingRequestEntity.class))).thenReturn(savedEntity);

        AppraiserPricingResponse response = appraiserPricingService.createRequest(sellerId, request);

        assertThat(response).isNotNull();
        assertThat(response.sellerId()).isEqualTo(sellerId);
        assertThat(response.appraiserId()).isEqualTo(appraiserUserId);
        assertThat(response.appraiserName()).isEqualTo("Chuyen Gia Tran Van B");
        assertThat(response.status()).isEqualTo(PricingStatus.PENDING);
        assertThat(response.pricingMode()).isEqualTo(PricingMode.APPRAISER);
    }

    @Test
    @DisplayName("Tạo yêu cầu định giá đẩy vào hàng đợi (không chỉ định appraiserId) thành công")
    void createRequest_Success_ToQueueWithoutAppraiser() {
        AppraiserPricingCreateRequest request = new AppraiserPricingCreateRequest(
                listingId, categoryId, null, BigDecimal.valueOf(2.0));

        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(pricingRequestRepository.existsByListingIdAndStatus(listingId, PricingStatus.PENDING)).thenReturn(false);
        when(pricingRequestRepository.countByListingIdAndStatus(listingId, PricingStatus.SELLER_REJECTED)).thenReturn(0L);

        PricingRequestEntity savedEntity = new PricingRequestEntity(
                sellerId, listingId, categoryId, null, BigDecimal.valueOf(2.0));
        when(pricingRequestRepository.save(any(PricingRequestEntity.class))).thenReturn(savedEntity);

        AppraiserPricingResponse response = appraiserPricingService.createRequest(sellerId, request);

        assertThat(response).isNotNull();
        assertThat(response.sellerId()).isEqualTo(sellerId);
        assertThat(response.appraiserId()).isNull();
        assertThat(response.status()).isEqualTo(PricingStatus.PENDING);
    }

    @Test
    @DisplayName("Chống xung đột lợi ích: Người bán không được tự định giá sản phẩm của chính mình")
    void createRequest_ConflictOfInterest_ThrowsException() {
        AppraiserPricingCreateRequest request = new AppraiserPricingCreateRequest(
                listingId, categoryId, appraiserProfile.getId(), BigDecimal.valueOf(3.5));

        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(pricingRequestRepository.existsByListingIdAndStatus(listingId, PricingStatus.PENDING)).thenReturn(false);
        when(pricingRequestRepository.countByListingIdAndStatus(listingId, PricingStatus.SELLER_REJECTED)).thenReturn(0L);

        // Giả sử sellerId chính là appraiserUserId
        when(appraiserProfileRepository.findById(appraiserProfile.getId())).thenReturn(Optional.of(appraiserProfile));

        assertThatThrownBy(() -> appraiserPricingService.createRequest(appraiserUserId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPRAISER_CANNOT_APPRAISE_OWN_PRODUCT);
    }

    @Test
    @DisplayName("Listing đã bị từ chối tối đa 3 lần thì chặn tạo mới (PRICING_005)")
    void createRequest_RejectLimitExceeded_ThrowsException() {
        AppraiserPricingCreateRequest request = new AppraiserPricingCreateRequest(
                listingId, categoryId, null, BigDecimal.valueOf(2.0));

        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(pricingRequestRepository.existsByListingIdAndStatus(listingId, PricingStatus.PENDING)).thenReturn(false);
        when(pricingRequestRepository.countByListingIdAndStatus(listingId, PricingStatus.SELLER_REJECTED)).thenReturn(3L);

        assertThatThrownBy(() -> appraiserPricingService.createRequest(sellerId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRICING_REJECT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Listing đang có yêu cầu PENDING thì chặn tạo mới (PRICING_007)")
    void createRequest_AlreadyPending_ThrowsException() {
        AppraiserPricingCreateRequest request = new AppraiserPricingCreateRequest(
                listingId, categoryId, null, BigDecimal.valueOf(2.0));

        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(pricingRequestRepository.existsByListingIdAndStatus(listingId, PricingStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> appraiserPricingService.createRequest(sellerId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRICING_REQUEST_ALREADY_PENDING);
    }

    @Test
    @DisplayName("Thẩm định viên nhận nhiệm vụ từ hàng đợi thành công (khóa nhiệm vụ - PRICING-10)")
    void claimTask_Success() {
        UUID requestId = UUID.randomUUID();
        PricingRequestEntity task = new PricingRequestEntity(
                sellerId, listingId, categoryId, null, BigDecimal.valueOf(2.0));

        when(pricingRequestRepository.findById(requestId)).thenReturn(Optional.of(task));
        when(appraiserProfileRepository.findByUserId(appraiserUserId)).thenReturn(Optional.of(appraiserProfile));
        when(appraiserCategoryRepository.existsByAppraiserIdAndCategoryId(appraiserProfile.getId(), categoryId)).thenReturn(true);
        when(pricingRequestRepository.save(task)).thenReturn(task);
        when(userRepository.findById(appraiserUserId)).thenReturn(Optional.of(appraiserUser));

        AppraiserPricingResponse response = appraiserPricingService.claimTask(appraiserUserId, requestId);

        assertThat(response.appraiserId()).isEqualTo(appraiserUserId);
        assertThat(task.getAppraiserId()).isEqualTo(appraiserUserId);
    }

    @Test
    @DisplayName("Nhiệm vụ đã được nhận bởi người khác thì ném lỗi PRICING_REQUEST_ALREADY_CLAIMED")
    void claimTask_AlreadyClaimed_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        UUID otherAppraiserId = UUID.randomUUID();
        PricingRequestEntity task = new PricingRequestEntity(
                sellerId, listingId, categoryId, otherAppraiserId, BigDecimal.valueOf(2.0));

        when(pricingRequestRepository.findById(requestId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appraiserPricingService.claimTask(appraiserUserId, requestId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRICING_REQUEST_ALREADY_CLAIMED);
    }

    @Test
    @DisplayName("Thẩm định viên gửi kết quả thẩm định thành công (PRICING-12)")
    void submitAppraisal_Success() {
        UUID requestId = UUID.randomUUID();
        PricingRequestEntity task = new PricingRequestEntity(
                sellerId, listingId, categoryId, appraiserUserId, BigDecimal.valueOf(2.0));

        when(pricingRequestRepository.findById(requestId)).thenReturn(Optional.of(task));
        when(appraiserProfileRepository.findByUserId(appraiserUserId)).thenReturn(Optional.of(appraiserProfile));
        when(userRepository.findById(appraiserUserId)).thenReturn(Optional.of(appraiserUser));
        when(pricingRequestRepository.save(task)).thenReturn(task);

        AppraiserPricingSubmitRequest submitRequest = new AppraiserPricingSubmitRequest(
                BigDecimal.valueOf(15000000), "Máy còn rất tốt, xước nhẹ viền.");

        AppraiserPricingResponse response = appraiserPricingService.submitAppraisal(
                appraiserUserId, requestId, submitRequest);

        assertThat(response.status()).isEqualTo(PricingStatus.SUGGESTED);
        assertThat(response.suggestedPrice()).isEqualByComparingTo(BigDecimal.valueOf(15000000));
        assertThat(response.appraiserNote()).isEqualTo("Máy còn rất tốt, xước nhẹ viền.");
        assertThat(response.disclaimer()).contains("Đã tham khảo giá bởi Chuyen Gia Tran Van B");
    }

    @Test
    @DisplayName("Người không phải thẩm định viên được gán thì không được submit (PRICING_008)")
    void submitAppraisal_NotAssignedAppraiser_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        UUID otherAppraiserId = UUID.randomUUID();
        PricingRequestEntity task = new PricingRequestEntity(
                sellerId, listingId, categoryId, otherAppraiserId, BigDecimal.valueOf(2.0));

        when(pricingRequestRepository.findById(requestId)).thenReturn(Optional.of(task));

        AppraiserPricingSubmitRequest submitRequest = new AppraiserPricingSubmitRequest(
                BigDecimal.valueOf(15000000), "Máy còn rất tốt.");

        assertThatThrownBy(() -> appraiserPricingService.submitAppraisal(appraiserUserId, requestId, submitRequest))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPRAISER_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("Người bán chấp nhận mức giá thẩm định thành công")
    void acceptAppraisal_Success() {
        UUID requestId = UUID.randomUUID();
        PricingRequestEntity task = new PricingRequestEntity(
                sellerId, listingId, categoryId, appraiserUserId, BigDecimal.valueOf(2.0));
        task.submitAppraiserPricing(BigDecimal.valueOf(15000000), "Note", "Disclaimer");

        when(pricingRequestRepository.findById(requestId)).thenReturn(Optional.of(task));
        when(pricingRequestRepository.save(task)).thenReturn(task);
        when(userRepository.findById(appraiserUserId)).thenReturn(Optional.of(appraiserUser));

        AppraiserPricingResponse response = appraiserPricingService.acceptAppraisal(sellerId, requestId);

        assertThat(response.status()).isEqualTo(PricingStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Người bán từ chối mức giá thẩm định thì tăng rejectCount")
    void rejectAppraisal_Success() {
        UUID requestId = UUID.randomUUID();
        PricingRequestEntity task = new PricingRequestEntity(
                sellerId, listingId, categoryId, appraiserUserId, BigDecimal.valueOf(2.0));
        task.submitAppraiserPricing(BigDecimal.valueOf(15000000), "Note", "Disclaimer");

        when(pricingRequestRepository.findById(requestId)).thenReturn(Optional.of(task));
        when(pricingRequestRepository.save(task)).thenReturn(task);
        when(userRepository.findById(appraiserUserId)).thenReturn(Optional.of(appraiserUser));

        AppraiserPricingResponse response = appraiserPricingService.rejectAppraisal(sellerId, requestId);

        assertThat(response.status()).isEqualTo(PricingStatus.SELLER_REJECTED);
        assertThat(task.getRejectCount()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("Không phải người bán thì không thể chấp nhận định giá")
    void acceptAppraisal_NotSeller_ThrowsException() {
        UUID requestId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        PricingRequestEntity task = new PricingRequestEntity(
                sellerId, listingId, categoryId, appraiserUserId, BigDecimal.valueOf(2.0));
        task.submitAppraiserPricing(BigDecimal.valueOf(15000000), "Note", "Disclaimer");

        when(pricingRequestRepository.findById(requestId)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> appraiserPricingService.acceptAppraisal(otherUserId, requestId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_REQUEST_SELLER);
    }
}
