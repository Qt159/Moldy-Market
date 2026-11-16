package com.moldy.lambda.notification;

import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public class SnsService {

    // ARN của SNS topic 
    private static final String TOPIC_ARN = System.getenv("SNS_TOPIC_ARN");

    private final SnsClient client;

    public SnsService(SnsClient client) {
        this.client = client;
    }

    public void publish(NotificationEvent event) {
        if (TOPIC_ARN == null) return;

        String message = String.format(
                "{\"userId\":\"%s\",\"title\":\"%s\",\"content\":\"%s\",\"type\":\"%s\"}",
                event.userId(), event.title(), event.content(), event.type().name()
        );

        PublishRequest request = PublishRequest.builder()
                .topicArn(TOPIC_ARN)
                .message(message)
                .subject(event.title())
                .build();

        client.publish(request);
    }
}
