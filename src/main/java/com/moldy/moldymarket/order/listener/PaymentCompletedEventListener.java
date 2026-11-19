package com.moldy.moldymarket.order.listener;

import com.moldy.moldymarket.order.service.CheckoutGroupService;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentCompletedEventListener {
    CheckoutGroupService checkoutGroupService;

    @EventListener
    public void handlePaymentCompleted(PaymentCompletedEvent paymentCompletedEvent){
        checkoutGroupService.markCheckoutGroupPaid(paymentCompletedEvent.checkoutGroupId());
    }

}
