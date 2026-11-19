package com.moldy.moldymarket.refund.listener;

import com.moldy.moldymarket.refund.dto.request.RefundCreationRequest;
import com.moldy.moldymarket.refund.event.RefundRequestEvent;
import com.moldy.moldymarket.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelOrderEventListener {
    RefundService refundService;

    @EventListener
    public void handleRefundRequestEvent(RefundRequestEvent event){
        refundService.createRefund(new RefundCreationRequest(event.orderId(), event.buyerId(), event.disputeId(), event.reason()));
    }
}
