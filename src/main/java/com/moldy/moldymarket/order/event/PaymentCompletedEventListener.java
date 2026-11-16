package com.moldy.moldymarket.order.event;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.order.service.CheckoutGroupService;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentCompletedEventListener {
    CheckoutGroupService checkoutGroupService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PaymentCompletedEvent paymentCompletedEvent){
        log.info("PaymentCompletedEvent received: {}",
                paymentCompletedEvent.checkoutGroupId());
        try{
            checkoutGroupService.markCheckoutGroupPaid(paymentCompletedEvent.checkoutGroupId());
        }
        catch (Exception e){
            log.error("Failed to process PaymentCompletedEvent. " + "checkoutGroupId={}", paymentCompletedEvent.checkoutGroupId(), e);
        }
    }

}
