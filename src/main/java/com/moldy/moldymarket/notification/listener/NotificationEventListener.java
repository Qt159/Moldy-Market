package com.moldy.moldymarket.notification.listener;

import com.moldy.moldymarket.notification.factory.NotificationEventFactory;
import com.moldy.moldymarket.notification.outbox.OutboxService;
import com.moldy.moldymarket.offer.event.OfferEvent;
import com.moldy.moldymarket.order.event.OrderCreatedEvent;
import com.moldy.moldymarket.order.event.OrderStatusChangedEvent;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import com.moldy.moldymarket.appraisal.assignment.event.AppraisalAssignedEvent;
import com.moldy.moldymarket.appraisal.invitation.event.AppraisalInvitationChangedEvent;
import com.moldy.moldymarket.appraisal.report.event.AppraisalReportSubmittedEvent;
import com.moldy.moldymarket.appraisal.review.event.AppraisalReviewCreatedEvent;
import com.moldy.moldymarket.wallet.event.WithdrawalChangedEvent;
import com.moldy.moldymarket.wallet.event.EscrowNotificationEvent;
import com.moldy.shared.notification.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;


@ConditionalOnProperty(name = "notifications.outbox.enabled", havingValue = "true")
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationEventFactory factory;
    private final OutboxService outboxService;

    @EventListener
    public void handle(OrderCreatedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(OrderStatusChangedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(PaymentCompletedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(AppraisalAssignedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(AppraisalInvitationChangedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(AppraisalReportSubmittedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(AppraisalReviewCreatedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(WithdrawalChangedEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(EscrowNotificationEvent event) {
        publishAll(factory.from(event));
    }

    @EventListener
    public void handle(OfferEvent event) { publishAll(factory.from(event)); }

    private void publishAll(
            java.util.List<NotificationEvent> events
    ) {
        outboxService.enqueue(events);
    }
}
