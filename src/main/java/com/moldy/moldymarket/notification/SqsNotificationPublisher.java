package com.moldy.moldymarket.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moldy.shared.notification.NotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
public class SqsNotificationPublisher implements NotificationPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public SqsNotificationPublisher(SqsClient sqsClient, ObjectMapper objectMapper,
            @Value("${aws.sqs.notification-queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publishNotification(NotificationEvent event) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build();

            sqsClient.sendMessage(request);

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize notification event", e);
        }
    }
}
