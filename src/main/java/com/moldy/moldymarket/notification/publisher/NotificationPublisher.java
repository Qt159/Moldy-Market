package com.moldy.moldymarket.notification.publisher;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.moldy.shared.notification.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.notification-queue-url}")
    private String queueUrl;

    public void publish(NotificationEvent event) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);

            SendMessageResponse response = sqsClient.sendMessage(
                    SendMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .messageBody(messageBody)
                            .build());

            log.info(
                    "Notification event published. notificationId={}, messageId={}",
                    event.notificationId(),
                    response.messageId());

        } catch (JacksonException exception) {
            log.error(
                    "Failed to serialize notification event. notificationId={}",
                    event.notificationId(),
                    exception);

            throw new IllegalStateException(
                    "Failed to serialize notification event",
                    exception);
        } catch (RuntimeException exception) {
            log.error("Failed to publish notification. notificationId={}",
                    event.notificationId(), exception);
            throw exception;
        }
    }
}
