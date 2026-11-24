package com.moldy.moldymarket.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.review.dto.CreateStoreReviewRequest;
import com.moldy.moldymarket.review.entity.StoreReview;
import com.moldy.moldymarket.review.repository.StoreReviewRepository;
import com.moldy.moldymarket.user.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StoreReviewServiceTest {

    @Mock StoreReviewRepository reviewRepository;
    @Mock OrderRepository orderRepository;
    @Mock CurrentUser currentUser;

    @InjectMocks StoreReviewService reviewService;

    private UUID buyerId;
    private UUID sellerId;
    private UUID storeId;
    private UUID orderId;
    private User buyer;
    private Order completedOrder;

    @BeforeEach
    void setUp() {
        buyerId = UUID.randomUUID();
        sellerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        orderId = UUID.randomUUID();

        buyer = new User();
        buyer.setId(buyerId);
        buyer.setFullName("Chu Nguyen");

        completedOrder = Order.builder()
                .buyerId(buyerId)
                .sellerId(sellerId)
                .storeId(storeId)
                .orderCode("ORD-REV01")
                .status(OrderStatus.COMPLETED)
                .totalAmount(BigDecimal.valueOf(200000))
                .shippingFee(BigDecimal.ZERO)
                .receiverName("Chu")
                .receiverPhone("0901234567")
                .shippingAddress("HCMC")
                .build();
        ReflectionTestUtils.setField(completedOrder, "id", orderId);
    }

    @Test
    void createReviewSavesReviewForCompletedOrder() {
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));
        when(reviewRepository.existsByOrderId(orderId)).thenReturn(false);
        when(reviewRepository.saveAndFlush(any(StoreReview.class))).thenAnswer(inv -> {
            StoreReview saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        var response = reviewService.createReview(new CreateStoreReviewRequest(orderId, 5, "  Great item!  "));

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Great item!");
        assertThat(response.buyerName()).isEqualTo("Chu Nguyen");
        assertThat(response.storeId()).isEqualTo(storeId);
    }

    @Test
    void createReviewRejectsNonCompletedOrder() {
        completedOrder.setStatus(OrderStatus.DELIVERED);
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));

        assertThatThrownBy(() -> reviewService.createReview(new CreateStoreReviewRequest(orderId, 5, null)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_ORDER_STATE);
    }

    @Test
    void createReviewRejectsDuplicateReview() {
        when(currentUser.findCurrentUser()).thenReturn(buyer);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(completedOrder));
        when(reviewRepository.existsByOrderId(orderId)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.createReview(new CreateStoreReviewRequest(orderId, 4, "Good")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    @Test
    void getStoreReviewsComputesRoundedAverageRating() {
        StoreReview r1 = StoreReview.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .storeId(storeId)
                .sellerId(sellerId)
                .buyerId(buyerId)
                .buyerName("Buyer A")
                .rating(5)
                .comment("Awesome")
                .createdAt(Instant.now())
                .build();
        StoreReview r2 = StoreReview.builder()
                .id(UUID.randomUUID())
                .orderId(UUID.randomUUID())
                .storeId(storeId)
                .sellerId(sellerId)
                .buyerId(UUID.randomUUID())
                .buyerName("Buyer B")
                .rating(4)
                .comment("Nice")
                .createdAt(Instant.now())
                .build();
        when(reviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId)).thenReturn(List.of(r1, r2));

        var summary = reviewService.getStoreReviews(storeId);

        assertThat(summary.storeId()).isEqualTo(storeId);
        assertThat(summary.reviewCount()).isEqualTo(2);
        assertThat(summary.averageRating()).isEqualTo(4.5);
        assertThat(summary.reviews()).hasSize(2);
    }
}
