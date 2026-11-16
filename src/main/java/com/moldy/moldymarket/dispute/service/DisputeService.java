package com.moldy.moldymarket.dispute.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.dispute.dto.request.DisputeCreationRequest;
import com.moldy.moldymarket.dispute.dto.request.ReviewDisputeRequest;
import com.moldy.moldymarket.dispute.dto.response.DisputeResponse;
import com.moldy.moldymarket.dispute.dto.response.DisputeSummaryResponse;
import com.moldy.moldymarket.dispute.entity.DisputeRequest;
import com.moldy.moldymarket.dispute.enums.DisputeStatus;
import com.moldy.moldymarket.dispute.mapper.DisputeMapper;
import com.moldy.moldymarket.dispute.repository.DisputeRepository;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.repository.OrderRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DisputeService {
    DisputeRepository disputeRepository;
    OrderRepository orderRepository;
    CurrentUser currentUser;
    DisputeMapper disputeMapper;

    //buyers
    @Transactional
    public DisputeResponse createDispute(DisputeCreationRequest request) {
        var user = currentUser.findCurrentUser();

        var order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        //validate order
        if(!order.getStatus().equals(OrderStatus.COMPLETED)){
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        if(order.getSellerId() == null) throw new AppException(ErrorCode.INVALID_ORDER);
        if(order.getReturnDeadline() == null || Instant.now().isBefore(order.getReturnDeadline()))
            throw new AppException(ErrorCode.DISPUTE_PERIOD_EXPIRED);
        var existingDispute = disputeRepository.findByOrderId(order.getId());
        DisputeRequest disputeRequest;
        if(existingDispute.isPresent()){
            disputeRequest = existingDispute.get();
            if(!disputeRequest.getStatus().equals(DisputeStatus.CANCELLED))
                throw new AppException(ErrorCode.DISPUTE_EXISTED);
            disputeRequest.reopen(request.reason(), request.description(), request.imageUrls());
        } else {
            disputeRequest = DisputeRequest.builder()
                    .buyerId(user.getId())
                    .sellerId(order.getSellerId())
                    .orderId(order.getId())
                    .reason(request.reason())
                    .description(request.description())
                    .imageUrls(request.imageUrls())
                    .build();
            disputeRepository.save(disputeRequest);
        }
        if(orderRepository.markDisputed(order.getId(), Instant.now()) != 1)
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);

        return disputeMapper.toDisputeResponse(disputeRequest);
    }

    @Transactional(readOnly = true)
    public DisputeResponse getMyDispute(UUID orderId){
        assertOrderOwner(orderId);
        var disputeRequest = disputeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_EXISTED));

        return disputeMapper.toDisputeResponse(disputeRequest);
    }

    @Transactional(readOnly = true)
    public List<DisputeSummaryResponse> getAllMyDispute(){
        var user = currentUser.findCurrentUser();

        return disputeRepository.findAllDisputeByBuyerId(user.getId()).stream().map(disputeMapper::toDisputeSummaryResponse).toList();
    }

    @Transactional
    public DisputeResponse cancelMyDispute(UUID orderId){
        assertOrderOwner(orderId);
        var disputeRequest = disputeRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_EXISTED));
        if(!disputeRequest.getStatus().equals(DisputeStatus.PENDING_APPROVAL) && !disputeRequest.getStatus().equals(DisputeStatus.OPEN))
            throw new AppException(ErrorCode.DISPUTE_INVALID_STATE);

        if(orderRepository.revertDisputed(orderId, Instant.now()) != 1){
            throw new AppException(ErrorCode.INVALID_ORDER_STATE);
        }
        disputeRequest.cancel();
        return disputeMapper.toDisputeResponse(disputeRequest);
    }

    //admin
    @Transactional(readOnly = true)
    public List<DisputeSummaryResponse> getDisputesForAdmin(){
        return disputeRepository.findAll().stream().map(disputeMapper::toDisputeSummaryResponse).toList();
    }
    @Transactional(readOnly = true)
    public DisputeResponse getDetailDisputeForAdmin(UUID id){
        var disputeRequest = disputeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_EXISTED));

        return disputeMapper.toDisputeResponse(disputeRequest);
    }
    @Transactional
    public DisputeResponse giveDecisionForDispute(UUID id, ReviewDisputeRequest request) {
        var user = currentUser.findCurrentUser();
        var disputeRequest = disputeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISPUTE_NOT_EXISTED));


        switch (request.decision()) {
            case APPROVE -> disputeRequest.approve(user.getId(), request.note());
            case REJECT -> disputeRequest.reject(user.getId(), request.note());
        }
        return disputeMapper.toDisputeResponse(disputeRequest);
    }

    //helpers
    private void assertOrderOwner(UUID orderId){
        var user = currentUser.findCurrentUser();
        var order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_EXISTED));

        if(!order.getBuyerId().equals(user.getId())) throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
    }
}
