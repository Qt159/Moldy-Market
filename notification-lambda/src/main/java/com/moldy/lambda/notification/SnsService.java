package com.moldy.lambda.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.LinkedHashMap;
import java.util.Map;

public class SnsService {
    private final String topicArn;
    private final SnsClient client;
    private final ObjectMapper objectMapper;

    public SnsService(SnsClient client, ObjectMapper objectMapper, String topicArn) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.topicArn = topicArn == null ? "" : topicArn.trim();
    }

    public boolean isEnabled() {
        return !topicArn.isBlank();
    }

    public void publish(NotificationEvent event) {
        if (!isEnabled()) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", event.notificationId());
        payload.put("userId", event.userId());
        payload.put("type", event.type().name());
        payload.put("referenceType", event.referenceType());
        payload.put("referenceId", event.referenceId());
        payload.put("title", event.title());
        payload.put("content", event.content());
        payload.put("createdAt", event.createdAt().toString());

        final String message;
        try {
            // Do not include recipientEmail in topic fan-out messages.
            message = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize SNS notification", e);
        }

        try {
            client.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .subject(event.title())
                    .messageAttributes(Map.of(
                            "notificationId", MessageAttributeValue.builder()
                                    .dataType("String").stringValue(event.notificationId()).build(),
                            "type", MessageAttributeValue.builder()
                                    .dataType("String").stringValue(event.type().name()).build()))
                    .build());
        } catch (SdkException e) {
            throw new RetryableException(
                    "SNS publish failed: " + event.notificationId(), e);
        }
    }
}
