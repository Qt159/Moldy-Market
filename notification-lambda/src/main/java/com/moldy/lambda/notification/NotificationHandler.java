package com.moldy.lambda.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Lambda handler — trigger: SQS (ReportBatchItemFailures phải được bật trên trigger)
 *
 * Trả về SQSBatchResponse thay vì Void để SQS chỉ retry đúng message thất bại,
 * không retry toàn bộ batch.
 *
 * Phân biệt 2 loại lỗi:
 *   - Non-retryable (JSON parse fail, dữ liệu thiếu): log + bỏ qua, không đưa vào failures
 *     vì retry cũng không giúp được gì.
 *   - Retryable (DynamoDB throttle, SES timeout, network): đưa messageId vào failures
 *     để SQS retry lại sau.
 */
public class NotificationHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final ObjectMapper objectMapper;
    private final DynamoDbService dynamoDbService;
    private final SesService sesService;
    private final SnsService snsService;

    public NotificationHandler() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                // Bỏ qua field lạ để khi schema thay đổi
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Lambda runtime tự inject AWS_REGION — fallback ap-south-1 cho nhất quán với infra
        Region region = Region.of(
                System.getenv().getOrDefault("AWS_REGION", "ap-south-1")
        );
        DefaultCredentialsProvider credentials = DefaultCredentialsProvider.create();

        DynamoDbClient dynamoDb = DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build();

        SesClient ses = SesClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build();

        SnsClient sns = SnsClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build();

        this.dynamoDbService = new DynamoDbService(dynamoDb);
        this.sesService = new SesService(ses);
        this.snsService = new SnsService(sns);
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                NotificationEvent notificationEvent = parseEvent(message, context);
                if (notificationEvent == null) {
                    // JSON invalid — bỏ qua, không retry
                    continue;
                }
                dynamoDbService.save(notificationEvent);

                if (shouldSendEmail(notificationEvent)) {
                    sesService.sendEmail(notificationEvent);
                }

                if (shouldPushNotification(notificationEvent)) {
                    snsService.publish(notificationEvent);
                }

                context.getLogger().log(String.format(
                        "[OK] msgId=%s notificationId=%s type=%s",
                        message.getMessageId(),
                        notificationEvent.notificationId(),
                        notificationEvent.type()
                ));

            } catch (RetryableException e) {
                // Các lỗi AWS SDK error, throttle, timeout — báo SQS retry
                context.getLogger().log(String.format(
                        "[RETRYABLE] msgId=%s error=%s",
                        message.getMessageId(), e.getMessage()
                ));
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());

            } catch (Exception e) {
                // Unexpected error — retry để an toàn
                context.getLogger().log(String.format(
                        "[ERROR] msgId=%s error=%s",
                        message.getMessageId(), e.getMessage()
                ));
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build());
            }
        }

        return SQSBatchResponse.builder()
                .withBatchItemFailures(failures)
                .build();
    }

    /*
        chuyển message body thành NotificationEvent.
        trả về null nếu JSON không hợp lệ (non-retryable).
     */
    private NotificationEvent parseEvent(SQSEvent.SQSMessage message, Context context) {
        try {
            return objectMapper.readValue(message.getBody(), NotificationEvent.class);
        } catch (Exception e) {
            context.getLogger().log(String.format(
                    "[SKIP] Non-retryable parse error msgId=%s error=%s",
                    message.getMessageId(), e.getMessage()
            ));
            return null;
        }
    }

    private boolean shouldSendEmail(NotificationEvent event) {
        return switch (event.type()) {
            case ORDER_COMPLETED,
                 PAYMENT_SUCCESS,
                 PAYMENT_FAILED,
                 ESCROW_RELEASED,
                 ESCROW_REFUNDED,
                 DISPUTE_OPENED,
                 DISPUTE_RESOLVED,
                 WITHDRAWAL_SUCCESS,
                 WITHDRAWAL_FAILED -> true;
            default -> false;
        };
    }

    private boolean shouldPushNotification(NotificationEvent event) {
        return switch (event.type()) {
            case ORDER_SHIPPED,
                 ORDER_DELIVERED,
                 OFFER_RECEIVED,
                 OFFER_ACCEPTED,
                 OFFER_REJECTED -> true;
            default -> false;
        };
    }
}
