package com.moldy.moldymarket.review.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.order.entity.Order;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import com.moldy.moldymarket.review.dto.CreateStoreReviewRequest;
import com.moldy.moldymarket.review.dto.StoreRatingSummaryResponse;
import com.moldy.moldymarket.review.dto.StoreReviewResponse;
import com.moldy.moldymarket.review.entity.StoreReview;
import com.moldy.moldymarket.review.repository.StoreReviewRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StoreReviewService {

    StoreReviewRepository reviewRepository;
    OrderRepository orderRepository;
    CurrentUser currentUser;

    @Transactional
    public StoreReviewResponse createReview(CreateStoreReviewRequest request) {
        var buyer = currentUser.findCurrentUser();
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if (!order.getBuyerId().equals(buyer.getId())) {
            throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
        if (reviewRepository.existsByOrderId(order.getId())) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        String trimmedComment = request.comment() != null && !request.comment().isBlank()
                ? request.comment().trim()
                : null;
        String buyerName = buyer.getFullName() != null && !buyer.getFullName().isBlank()
                ? buyer.getFullName().trim()
                : "Buyer";

        StoreReview review = StoreReview.builder()
                .orderId(order.getId())
                .storeId(order.getStoreId())
                .sellerId(order.getSellerId())
                .buyerId(buyer.getId())
                .buyerName(buyerName)
                .rating(request.rating())
                .comment(trimmedComment)
                .build();

        try {
            return StoreReviewResponse.from(reviewRepository.saveAndFlush(review));
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public StoreReviewResponse getReviewByOrderId(UUID orderId) {
        UUID buyerId = currentUser.findCurrentUser().getId();
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if (!order.getBuyerId().equals(buyerId)) {
            throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
        }

        return reviewRepository.findByOrderId(orderId)
                .map(StoreReviewResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public StoreRatingSummaryResponse getStoreReviews(UUID storeId) {
        List<StoreReview> reviews = reviewRepository.findAllByStoreIdOrderByCreatedAtDesc(storeId);
        double average = 0.0;
        if (!reviews.isEmpty()) {
            double rawAvg = reviews.stream()
                    .mapToInt(StoreReview::getRating)
                    .average()
                    .orElse(0.0);
            average = Math.round(rawAvg * 10.0) / 10.0;
        }

        List<StoreReviewResponse> responses = reviews.stream()
                .map(StoreReviewResponse::from)
                .toList();

        return new StoreRatingSummaryResponse(storeId, average, responses.size(), responses);
    }
}
