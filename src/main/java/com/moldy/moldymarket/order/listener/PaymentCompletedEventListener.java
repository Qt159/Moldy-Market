package com.moldy.moldymarket.order.listener;

import com.moldy.moldymarket.order.service.CheckoutPaymentService;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCompletedEventListener {
    private final CheckoutPaymentService checkoutPaymentService;

    // Synchronous: a failed order/cart update must roll back the incoming payment too.
    @EventListener
    public void handle(PaymentCompletedEvent event) {
        checkoutPaymentService.confirmOnline(event.checkoutGroupId());
    }
}
