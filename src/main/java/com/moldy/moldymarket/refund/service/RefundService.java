package com.moldy.moldymarket.refund.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.config.CurrentUser;
import com.moldy.moldymarket.refund.dto.request.RefundCreationRequest;
import com.moldy.moldymarket.refund.dto.response.RefundResponse;
import com.moldy.moldymarket.refund.entity.RefundRequest;
import com.moldy.moldymarket.refund.enums.RefundDestination;
import com.moldy.moldymarket.refund.enums.RefundReason;
import com.moldy.moldymarket.refund.event.RefundCompletedEvent;
import com.moldy.moldymarket.refund.mapper.RefundMapper;
import com.moldy.moldymarket.refund.repository.RefundRepository;
import com.moldy.moldymarket.user.service.UserService;
import com.moldy.moldymarket.wallet.service.EscrowService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefundService {
    RefundRepository refundRepository;
    RefundMapper refundMapper;
    EscrowService escrowService;
    UserService userService;
    CurrentUser currentUser;
    ApplicationEventPublisher eventPublisher;

    @Transactional
    public void createRefund(RefundCreationRequest request){
        if(refundRepository.findByOrderId(request.orderId()).isPresent()) return;

        var bank = userService.getBankInfo(request.buyerId());
        var destination = bank.isPresent() ? RefundDestination.BANK : RefundDestination.WALLET;
        RefundRequest refundRequest = RefundRequest.builder()
                .orderId(request.orderId())
                .buyerId(request.buyerId())
                .amount(request.amount())
                .reason(request.reason())
                .destination(destination)
                .build();
        bank.ifPresent(b -> refundRequest.snapshotBank(b.bankName(), b.bankAccountNumber()));
        refundRepository.save(refundRequest);
    }

    //admin
    @Transactional
    public RefundResponse confirmRefund(UUID id){
        var admin = currentUser.findCurrentUser();
        var refund = getForUpdate(id);

        refund.confirm(admin.getId());

        //if users don't have bank account or haven't linked their bank yet, the money will go to their wallet
        if(refund.getDestination().equals(RefundDestination.WALLET)){
            escrowService.refundToBuyerWallet(refund.getOrderId());
            eventPublisher.publishEvent(new RefundCompletedEvent(refund.getOrderId(), refund.getReason(), refund.getDestination(), "Successfully refund"));
        }
        //for bank, admin have to transfer and click on completed refund (must submit reference)
        return refundMapper.toRefundResponse(refund);
    }


    @Transactional
    public RefundResponse markCompletedRefundRequest(){

    }

    @Transactional(readOnly = true)
    public RefundResponse viewDetailRefund(){

    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getAllRefundsForAdmin(){
        return refundRepository.findAll().stream().map(refundMapper::toRefundResponse).toList();
    }

    // buyers
    @Transactional(readOnly = true)
    public RefundResponse getMyRefunds() {
        var user = currentUser.findCurrentUser();
        return refundMapper.toRefundResponse(refundRepository.findByBuyerId(user.getId()));
    }

    //helpers
    private RefundRequest getForUpdate(UUID id){
        return refundRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.REFUND_NOT_EXISTED));
    }
}
