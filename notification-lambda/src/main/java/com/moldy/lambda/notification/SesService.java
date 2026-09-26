package com.moldy.lambda.notification;

import com.moldy.shared.notification.NotificationEvent;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

public class SesService {

        // dùng mail cá nhân trước
    private static final String SENDER = requireEnvironmentVariable("SES_SENDER_EMAIL");
    private static final String DEV_RECIPIENT_EMAIL =
            requireEnvironmentVariable("DEV_RECIPIENT_EMAIL");

    private final SesClient client;

    public SesService(SesClient client) {
        this.client = client;
    }

    public void sendEmail(NotificationEvent event) {
        String recipientEmail = resolveEmail(event.userId());

        SendEmailRequest request = SendEmailRequest.builder()
                .source(SENDER)
                .destination(Destination.builder()
                        .toAddresses(recipientEmail)
                        .build())
                .message(Message.builder()
                        .subject(Content.builder()
                                .data(event.title())
                                .charset("UTF-8")
                                .build())
                        .body(Body.builder()
                                .text(Content.builder()
                                        .data(buildBody(event))
                                        .charset("UTF-8")
                                        .build())
                                .build())
                        .build())
                .build();

        client.sendEmail(request);

        System.out.printf(
                "[SES] Email sent to=%s type=%s notificationId=%s%n",
                recipientEmail,
                event.type(),
                event.notificationId()
        );
    }

    /**
     * Staging: gửi tất cả notification về email cá nhân.
     * Production: thay bằng lookup email theo userId từ User module.
     */
    private String resolveEmail(String userId) {
        return DEV_RECIPIENT_EMAIL;
    }

    private String buildBody(NotificationEvent event) {
        return String.format(
                "%s\n\n---\nType: %s\nReference: %s / %s\nTime: %s",
                event.content(),
                event.type(),
                event.referenceType(),
                event.referenceId(),
                event.createdAt()
        );
    }

    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable " + name + " is required"
            );
        }

        return value;
    }
}