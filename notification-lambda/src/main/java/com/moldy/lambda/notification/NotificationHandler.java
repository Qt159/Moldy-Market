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

import java.util.ArrayList;
import java.util.List;

public class NotificationHandler
        implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private final ObjectMapper objectMapper;
    private final DynamoDbService dynamoDbService;
    private final SesService sesService;

    public NotificationHandler() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Region region = Region.of(
                System.getenv().getOrDefault("AWS_REGION", "ap-south-1"));

        DefaultCredentialsProvider credentials =
                DefaultCredentialsProvider.create();

        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build();

        SesClient sesClient = SesClient.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build();

        this.dynamoDbService = new DynamoDbService(dynamoDbClient);
        this.sesService = new SesService(sesClient);
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                NotificationEvent notification =
                        parseEvent(message, context);

                if (notification == null) {
                    // Let the SQS redrive policy move malformed messages to the DLQ.
                    addFailure(failures, message);
                    continue;
                }

                // luu notification history trước.
                dynamoDbService.saveNotification(notification);

                // Chỉ gửi email cho event được chọn.
                if (shouldSendEmail(notification)) {
                    processEmail(notification);
                }

                context.getLogger().log(String.format(
                        "[OK] messageId=%s notificationId=%s type=%s%n",
                        message.getMessageId(),
                        notification.notificationId(),
                        notification.type()
                ));

            } catch (RetryableException e) {
                logError(context, "RETRYABLE", message, e);
                addFailure(failures, message);

            } catch (Exception e) {
                logError(context, "ERROR", message, e);
                addFailure(failures, message);
            }
        }

        return SQSBatchResponse.builder()
                .withBatchItemFailures(failures)
                .build();
    }

    private void processEmail(NotificationEvent event) {
        boolean claimed = dynamoDbService.claimEmailDelivery(event);

        if (!claimed) {
            // Đang được xử lý hoặc đã gửi thành công.
            return;
        }

        try {
            sesService.sendEmail(event);
            dynamoDbService.markEmailSent(event);

        } catch (RuntimeException e) {
            // Đánh dấu FAILED để lần retry có thể claim lại.
            dynamoDbService.markEmailFailed(
                    event,
                    e.getMessage()
            );

            throw e;
        }
    }

    private NotificationEvent parseEvent(SQSEvent.SQSMessage message, Context context) {
        try {
            NotificationEvent notification =
                    objectMapper.readValue(
                            message.getBody(),
                            NotificationEvent.class);

            validateEvent(notification);
            return notification;

        } catch (Exception e) {
            context.getLogger().log(String.format(
                    "[INVALID] messageId=%s reason=%s%n",
                    message.getMessageId(),
                    e.getMessage()
            ));

            return null;
        }
    }

    private void validateEvent(NotificationEvent event) {
        if (event == null
                || event.notificationId() == null
                || event.userId() == null
                || event.type() == null
                || event.title() == null
                || event.content() == null
                || event.createdAt() == null) {
            throw new IllegalArgumentException(
                    "Notification event contains missing required fields"
            );
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

    private void addFailure(
            List<SQSBatchResponse.BatchItemFailure> failures,
            SQSEvent.SQSMessage message
    ) {
        failures.add(
                SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(message.getMessageId())
                        .build()
        );
    }

    private void logError(
            Context context,
            String level,
            SQSEvent.SQSMessage message,
            Exception e
    ) {
        context.getLogger().log(String.format(
                "[%s] messageId=%s error=%s%n",
                level,
                message.getMessageId(),
                e.getMessage()
        ));
    }
}
