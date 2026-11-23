package com.moldy.moldymarket.notification.factory;

import com.moldy.moldymarket.appraisal.assignment.event.AppraisalAssignedEvent;
import com.moldy.moldymarket.appraisal.invitation.enums.AppraisalInvitationStatus;
import com.moldy.moldymarket.appraisal.invitation.event.AppraisalInvitationChangedEvent;
import com.moldy.moldymarket.appraisal.report.event.AppraisalReportSubmittedEvent;
import com.moldy.moldymarket.appraisal.review.event.AppraisalReviewCreatedEvent;
import com.moldy.moldymarket.order.enums.OrderStatus;
import com.moldy.moldymarket.order.event.OrderCreatedEvent;
import com.moldy.moldymarket.order.event.OrderStatusChangedEvent;
import com.moldy.moldymarket.payment.event.PaymentCompletedEvent;
import com.moldy.moldymarket.wallet.enums.DisputeOutcome;
import com.moldy.moldymarket.wallet.enums.WithdrawalStatus;
import com.moldy.moldymarket.wallet.event.EscrowNotificationEvent;
import com.moldy.moldymarket.wallet.event.WithdrawalChangedEvent;
import com.moldy.shared.notification.NotificationEvent;
import com.moldy.shared.notification.NotificationType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class NotificationEventFactory {

    public List<NotificationEvent> from(OrderCreatedEvent event) {
        if (event == null || event.orderId() == null || event.sellerId() == null
                || isBlank(event.orderCode())) {
            return List.of();
        }
        return List.of(create(
                event.sellerId(), NotificationType.ORDER_CREATED,
                "ORDER", event.orderId().toString(), event.orderId().toString(),
                "Đơn hàng mới", "Bạn vừa nhận được đơn hàng #" + event.orderCode() + "."));
    }

    public List<NotificationEvent> from(OrderStatusChangedEvent event) {
        if (event == null || event.orderId() == null || event.oldStatus() == null
                || event.newStatus() == null || event.newStatus() == event.oldStatus()
                || event.buyerId() == null || event.sellerId() == null
                || event.buyerId().equals(event.sellerId())) {
            return List.of();
        }

        NotificationType type = mapOrderStatus(event.newStatus());
        if (type == null || (event.actorId() != null
                && !event.actorId().equals(event.buyerId())
                && !event.actorId().equals(event.sellerId()))) {
            return List.of();
        }

        UUID recipient;
        if (event.newStatus() == OrderStatus.CANCELLED) {
            if (event.actorId() == null) {
                return List.of();
            }
            recipient = event.actorId().equals(event.buyerId())
                    ? event.sellerId() : event.buyerId();
        } else {
            recipient = event.buyerId();
        }

        return List.of(create(
                recipient, type, "ORDER", event.orderId().toString(),
                event.orderId() + ":" + event.newStatus(),
                getOrderTitle(type), getOrderContent(type)));
    }

    public List<NotificationEvent> from(PaymentCompletedEvent event) {
        if (event == null || event.paymentId() == null || event.payerId() == null) {
            return List.of();
        }
        return List.of(create(
                event.payerId(), NotificationType.PAYMENT_SUCCESS,
                "PAYMENT", event.paymentId().toString(), event.paymentId().toString(),
                "Thanh toán thành công", "Thanh toán cho đơn hàng đã thành công."));
    }

    public List<NotificationEvent> from(AppraisalAssignedEvent event) {
        if (event == null || event.assignmentId() == null || event.sellerId() == null
                || event.appraiserId() == null || event.sellerId().equals(event.appraiserId())
                // Assignments are created by the seller in the current workflow.
                || !event.sellerId().equals(event.actorId())) {
            return List.of();
        }
        return List.of(create(
                event.appraiserId(), NotificationType.APPRAISAL_ASSIGNED,
                "APPRAISAL_ASSIGNMENT", event.assignmentId().toString(),
                event.assignmentId().toString(), "Đã phân công thẩm định",
                "Yêu cầu thẩm định đã được ghép với bạn."));
    }

    public List<NotificationEvent> from(AppraisalInvitationChangedEvent event) {
        if (event == null || event.invitationId() == null || event.sellerId() == null
                || event.appraiserId() == null || event.sellerId().equals(event.appraiserId())
                || event.status() == null) {
            return List.of();
        }

        NotificationType type;
        UUID recipient;
        String title;
        String content;
        switch (event.status()) {
            case PENDING -> {
                if (!event.sellerId().equals(event.actorId())) return List.of();
                type = NotificationType.OFFER_RECEIVED;
                recipient = event.appraiserId();
                title = "Bạn có lời mời thẩm định mới";
                content = "Người bán đã gửi lời mời tham gia yêu cầu thẩm định.";
            }
            case ACCEPTED -> {
                if (!event.appraiserId().equals(event.actorId())) return List.of();
                type = NotificationType.OFFER_ACCEPTED;
                recipient = event.sellerId();
                title = "Lời mời thẩm định đã được chấp nhận";
                content = "Người thẩm định đã chấp nhận lời mời của bạn.";
            }
            case REJECTED -> {
                if (!event.appraiserId().equals(event.actorId())) return List.of();
                type = NotificationType.OFFER_REJECTED;
                recipient = event.sellerId();
                title = "Lời mời thẩm định đã bị từ chối";
                content = "Người thẩm định đã từ chối lời mời của bạn.";
            }
            case CANCELLED -> {
                if (!event.sellerId().equals(event.actorId())) return List.of();
                type = NotificationType.OFFER_CANCELLED;
                recipient = event.appraiserId();
                title = "Lời mời thẩm định đã bị hủy";
                content = "Người bán đã hủy lời mời thẩm định.";
            }
            case EXPIRED -> {
                // Expiration is an automatic transition; the event has no actor for it.
                if (event.actorId() != null) return List.of();
                type = NotificationType.OFFER_EXPIRED;
                recipient = event.sellerId();
                title = "Lời mời thẩm định đã hết hạn";
                content = "Lời mời thẩm định đã hết hạn mà chưa được xử lý.";
            }
            default -> { return List.of(); }
        }

        return List.of(create(
                recipient, type, "APPRAISAL_INVITATION", event.invitationId().toString(),
                event.invitationId() + ":" + event.status(), title, content));
    }

    public List<NotificationEvent> from(AppraisalReportSubmittedEvent event) {
        if (event == null || event.reportId() == null || event.sellerId() == null
                || event.appraiserId() == null || event.sellerId().equals(event.appraiserId())) {
            return List.of();
        }
        return List.of(create(
                event.sellerId(), NotificationType.APPRAISAL_COMPLETED,
                "APPRAISAL_REPORT", event.reportId().toString(), event.reportId().toString(),
                "Báo cáo thẩm định đã sẵn sàng",
                "Người thẩm định đã gửi báo cáo. Bạn có thể xem và phản hồi."));
    }

    public List<NotificationEvent> from(AppraisalReviewCreatedEvent event) {
        if (event == null || event.reviewId() == null || event.sellerId() == null
                || event.appraiserId() == null || event.sellerId().equals(event.appraiserId())) {
            return List.of();
        }
        return List.of(create(
                event.appraiserId(), NotificationType.REVIEW_RECEIVED,
                "APPRAISAL_REVIEW", event.reviewId().toString(), event.reviewId().toString(),
                "Bạn vừa nhận được đánh giá",
                "Người bán đã gửi đánh giá sau phiên thẩm định."));
    }

    public List<NotificationEvent> from(WithdrawalChangedEvent event) {
        if (event == null || event.withdrawalId() == null || event.userId() == null
                || event.status() == null) {
            return List.of();
        }

        NotificationType type;
        String title;
        String content;
        switch (event.status()) {
            case REQUESTED -> {
                type = NotificationType.WITHDRAWAL_REQUESTED;
                title = "Đã tiếp nhận yêu cầu rút tiền";
                content = "Yêu cầu rút tiền của bạn đang được xử lý.";
            }
            case PROCESSING -> {
                type = NotificationType.WITHDRAWAL_REQUESTED;
                title = "Yêu cầu rút tiền đang được xử lý";
                content = "Yêu cầu rút tiền của bạn đang được xử lý.";
            }
            case COMPLETED -> {
                type = NotificationType.WITHDRAWAL_SUCCESS;
                title = "Rút tiền thành công";
                content = "Yêu cầu rút tiền của bạn đã hoàn tất.";
            }
            case FAILED, REJECTED -> {
                type = NotificationType.WITHDRAWAL_FAILED;
                title = "Yêu cầu rút tiền chưa hoàn tất";
                content = "Yêu cầu rút tiền của bạn đã bị từ chối hoặc thất bại.";
            }
            default -> { return List.of(); }
        }
        return List.of(create(
                event.userId(), type, "WITHDRAWAL", event.withdrawalId().toString(),
                event.withdrawalId() + ":" + event.status(), title, content));
    }

    public List<NotificationEvent> from(EscrowNotificationEvent event) {
        if (event == null || isBlank(event.orderId()) || event.action() == null) {
            return List.of();
        }
        return switch (event.action()) {
            case SELLER_PAID -> event.sellerId() == null ? List.of() : List.of(create(
                    event.sellerId(), NotificationType.ESCROW_RELEASED,
                    "ORDER", event.orderId(), event.orderId() + ":seller-paid",
                    "Tiền đơn hàng đã được chuyển", "Tiền từ đơn hàng đã được chuyển vào ví của bạn."));
            case BUYER_REFUNDED -> event.buyerId() == null ? List.of() : List.of(create(
                    event.buyerId(), NotificationType.ESCROW_REFUNDED,
                    "ORDER", event.orderId(), event.orderId() + ":buyer-refunded",
                    "Tiền đơn hàng đã được hoàn", "Tiền từ đơn hàng đã được hoàn vào ví của bạn."));
            case DISPUTE_OPENED -> createForDistinctParticipants(
                    event, NotificationType.DISPUTE_OPENED, event.orderId() + ":dispute-opened",
                    "Đơn hàng đang được xử lý tranh chấp",
                    "Một tranh chấp đã được mở cho đơn hàng này.");
            case DISPUTE_RESOLVED -> {
                if (event.outcome() == null) yield List.of();
                String outcome = event.outcome() == DisputeOutcome.PAY_SELLER
                        ? "Quyết định: thanh toán cho người bán."
                        : "Quyết định: hoàn tiền cho người mua.";
                yield createForDistinctParticipants(
                        event, NotificationType.DISPUTE_RESOLVED,
                        event.orderId() + ":dispute-resolved:" + event.outcome(),
                        "Tranh chấp đơn hàng đã được giải quyết", outcome);
            }
        };
    }

    private List<NotificationEvent> createForDistinctParticipants(
            EscrowNotificationEvent event, NotificationType type, String eventKey,
            String title, String content) {
        if (event.buyerId() == null || event.sellerId() == null) return List.of();
        List<NotificationEvent> notifications = new ArrayList<>(2);
        notifications.add(create(event.buyerId(), type, "ORDER", event.orderId(), eventKey, title, content));
        if (!event.sellerId().equals(event.buyerId())) {
            notifications.add(create(event.sellerId(), type, "ORDER", event.orderId(), eventKey, title, content));
        }
        return List.copyOf(notifications);
    }

    private NotificationEvent create(
            UUID recipientId, NotificationType type, String referenceType,
            String referenceId, String eventKey, String title, String content) {
        String notificationId = UUID.nameUUIDFromBytes(
                (type.name() + ":" + eventKey + ":" + recipientId)
                        .getBytes(StandardCharsets.UTF_8)).toString();
        return new NotificationEvent(notificationId, recipientId.toString(), type,
                referenceType, referenceId, title, content, Instant.now());
    }

    private NotificationType mapOrderStatus(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> NotificationType.ORDER_CONFIRMED;
            case SHIPPING -> NotificationType.ORDER_SHIPPED;
            case DELIVERED -> NotificationType.ORDER_DELIVERED;
            case COMPLETED -> NotificationType.ORDER_COMPLETED;
            case CANCELLED -> NotificationType.ORDER_CANCELLED;
            // Payment and dispute/refund events have dedicated event mappings.
            case PENDING_PAYMENT, PENDING_CONFIRMED, EXPIRED, DISPUTED, REFUNDED -> null;
        };
    }

    private String getOrderTitle(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMED -> "Đơn hàng đã được xác nhận";
            case ORDER_SHIPPED -> "Đơn hàng đã được giao cho đơn vị vận chuyển";
            case ORDER_DELIVERED -> "Đơn hàng đã được giao";
            case ORDER_COMPLETED -> "Đơn hàng đã hoàn thành";
            case ORDER_CANCELLED -> "Đơn hàng đã bị hủy";
            default -> "Cập nhật đơn hàng";
        };
    }

    private String getOrderContent(NotificationType type) {
        return switch (type) {
            case ORDER_CONFIRMED -> "Người bán đã xác nhận đơn hàng của bạn.";
            case ORDER_SHIPPED -> "Đơn hàng của bạn đã được gửi đi.";
            case ORDER_DELIVERED -> "Đơn hàng của bạn đã được giao.";
            case ORDER_COMPLETED -> "Đơn hàng của bạn đã hoàn thành.";
            case ORDER_CANCELLED -> "Đơn hàng đã bị hủy.";
            default -> "Đơn hàng vừa có cập nhật mới.";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
