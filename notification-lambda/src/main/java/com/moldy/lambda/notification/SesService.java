
package com.moldy.lambda.notification;

import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

public class SesService {

    private static final String SENDER = requireEnvironmentVariable("SES_SENDER_EMAIL");

    private static final String DEV_RECIPIENT_EMAIL = 
                requireEnvironmentVariable("DEV_RECIPIENT_EMAIL");
    private final SesClient client;

    public SesService(SesClient client) {
        this.client = client;
    }

    public void sendEmail(NotificationEvent event) {
        String recipient = resolveEmail(event.userId());

        SendEmailRequest request = SendEmailRequest.builder()
                .source(SENDER)
                .destination(Destination.builder()
                        .toAddresses(recipient)
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

        try {
            client.sendEmail(request);

        } catch (SdkException e) {
            throw new RetryableException(
                    "SES email sending failed: "
                            + event.notificationId(),
                    e
            );
        }
    }

    private String resolveEmail(String userId) {
        // Staging only.
        // Production: lookup email from User module.
        return DEV_RECIPIENT_EMAIL;
    }

    private String buildBody(NotificationEvent event) {
        String reference = event.referenceType() == null
                ? "N/A"
                : event.referenceType() + " / "
                    + (event.referenceId() == null
                        ? "N/A"
                        : event.referenceId());

        return String.format(
                "%s%n%n--------------------%nType: %s%nReference: %s%nTime: %s",
                event.content(),
                event.type(),
                reference,
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
