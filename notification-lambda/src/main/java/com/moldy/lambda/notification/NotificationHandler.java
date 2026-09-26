package com.moldy.lambda.notification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Lambda handler — trigger: SQS
 * Mỗi message trong SQS batch là một NotificationEvent JSON.
 * Với mỗi event: lưu DynamoDB → gửi SES email → push SNS.
 */
public class NotificationHandler implements RequestHandler<SQSEvent, Void> {

    private final ObjectMapper objectMapper;
    private final DynamoDbService dynamoDbService;
    private final SesService sesService;
    private final SnsService snsService;

    // Constructor không tham số — Lambda tự khởi tạo
    public NotificationHandler() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());

        Region region = Region.AP_SOUTHEAST_1;
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
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                NotificationEvent notificationEvent = objectMapper.readValue(
                        message.getBody(), NotificationEvent.class);

                // 1. Lưu vào DynamoDB
                dynamoDbService.save(notificationEvent);

                // 2. Gửi email qua SES (nếu type cần email)
                if (shouldSendEmail(notificationEvent)) {
                    sesService.sendEmail(notificationEvent);
                }

                // 3. Push notification qua SNS (nếu type cần push)
                if (shouldPushNotification(notificationEvent)) {
                    snsService.publish(notificationEvent);
                }

                context.getLogger().log("Processed notification: " + notificationEvent.notificationId());

            } catch (Exception e) {
                // Log lỗi nhưng không throw — tránh retry toàn bộ batch
                context.getLogger().log("ERROR processing message: " + e.getMessage());
            }
        }
        return null;
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
