package com.moldy.lambda.notification;

import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
public class SesService {

    private static final String SENDER = System.getenv("SES_SENDER_EMAIL");

    /* mail cá nhân nhận tất cả notification khi staging.
        Sau khi User module sẵn sàng: thay resolveEmail()

     */
    private static final String DEV_RECIPIENT_EMAIL = System.getenv("DEV_RECIPIENT_EMAIL");

    private final SesClient client;

    public SesService(SesClient client) {
        this.client = client;
    }

    public void sendEmail(NotificationEvent event) {
        String recipientEmail = resolveEmail(event.userId());
        if (recipientEmail == null) {
            // Log để biết email bị skip, không throw
            System.out.printf("[SES] Skip send email — no recipient resolved for userId=%s type=%s%n",
                    event.userId(), event.type());
            return;
        }

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
        System.out.printf("[SES] Email sent to=%s type=%s notificationId=%s%n",
                recipientEmail, event.type(), event.notificationId());
    }

    /**
     * Staging: gửi về mail cá nhân từ env DEV_RECIPIENT_EMAIL.
     * Production (khi có User module): lookup email theo userId từ DB/cache.
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
}                                       .data(event.content())
                                        .charset("UTF-8")
                                        .build())
                                .build())
                        .build())
                .build();

        client.sendEmail(request);
    }

    private String resolveEmail(String userId) {
        // TODO: đợi có userService để tìm user từ id 
        return null;
    }

    private static String requireSenderEmail() {
        String senderEmail = System.getenv("SES_SENDER_EMAIL");

        if (senderEmail == null || senderEmail.isBlank()) {
            throw new IllegalStateException(
                    "Environment variable SES_SENDER_EMAIL is required"
            );
        }
        return senderEmail;
    }
}