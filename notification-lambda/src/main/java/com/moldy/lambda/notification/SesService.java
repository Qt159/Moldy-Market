package com.moldy.lambda.notification;

import com.moldy.shared.notification.NotificationEvent;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

public class SesService {

    private static final String SENDER = System.getenv("SES_SENDER_EMAIL");

    private final SesClient client;

    public SesService(SesClient client) {
        this.client = client;
    }

    public void sendEmail(NotificationEvent event) {
        // userEmail cần được lookup từ DB hoặc truyền vào event
        // Hiện tại dùng placeholder — sẽ wire thêm sau khi có UserService
        String recipientEmail = resolveEmail(event.userId());
        if (recipientEmail == null) return;

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
                                        .data(event.content())
                                        .charset("UTF-8")
                                        .build())
                                .build())
                        .build())
                .build();

        client.sendEmail(request);
    }

    /**
     * TODO: lookup email từ user service hoặc truyền email vào NotificationEvent.
     * Tạm thời trả về null để bỏ qua cho đến khi có user lookup.
     */
    private String resolveEmail(String userId) {
        return null;
    }
}
