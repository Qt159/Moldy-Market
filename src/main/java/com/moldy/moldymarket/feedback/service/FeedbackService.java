package com.moldy.moldymarket.feedback.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.feedback.dto.request.FeedbackCreationRequest;
import com.moldy.moldymarket.feedback.dto.request.FeedbackUpdateRequest;
import com.moldy.moldymarket.feedback.dto.response.FeedbackResponse;
import com.moldy.moldymarket.feedback.entity.Feedback;
import com.moldy.moldymarket.feedback.mapper.FeedbackMapper;
import com.moldy.moldymarket.feedback.repository.FeedbackRepository;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FeedbackService {
    FeedbackRepository feedbackRepository;
    CurrentUser currentUser;
    OrderRepository orderRepository;
    FeedbackMapper feedbackMapper;

    @Transactional
    public FeedbackResponse createFeedback(FeedbackCreationRequest request){
        var user = currentUser.findCurrentUser();

        var order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_NOT_EXISTED);
        if (!order.getStatus().equals(OrderStatus.COMPLETED)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }

        if (feedbackRepository.existsByOrderIdAndBuyerId(
                order.getId(), user.getId())) {
            throw new AppException(ErrorCode.FEEDBACK_EXISTED);
        }

        var feedback = Feedback.builder()
                .orderId(order.getId())
                .buyerId(user.getId())
                .sellerId(order.getSellerId())
                .rating(request.rating())
                .comment(request.comment())
                .build();

        feedbackRepository.save(feedback);

        return feedbackMapper.toFeedbackResponse(feedback);

    }

    @Transactional
    public FeedbackResponse updateFeedback(UUID feedbackId,
            FeedbackUpdateRequest request) {

        var user = currentUser.findCurrentUser();

        var feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.FEEDBACK_NOT_EXISTED));

        if (!feedback.getBuyerId().equals(user.getId())) {
            throw new AppException(
                    ErrorCode.FEEDBACK_NOT_EXISTED);
        }

        feedback.setRating(request.rating());
        feedback.setComment(request.comment());

        return feedbackMapper.toFeedbackResponse(feedback);
    }

    @Transactional
    public void deleteFeedback(UUID feedbackId) {

        var user = currentUser.findCurrentUser();

        var feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() ->
                        new AppException(ErrorCode.FEEDBACK_NOT_EXISTED));

        if (!feedback.getBuyerId().equals(user.getId())) {
            throw new AppException(
                    ErrorCode.FEEDBACK_NOT_EXISTED);
        }

        feedbackRepository.delete(feedback);
    }
}
