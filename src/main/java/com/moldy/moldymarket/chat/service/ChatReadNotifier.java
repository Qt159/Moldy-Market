package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.dto.response.ChatReadReceipt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatReadNotifier {
    private final SimpMessagingTemplate messaging;

    public record ReadCommitted(ChatReadReceipt receipt, UUID peerId) {}

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyParticipants(ReadCommitted event) {
        for (UUID user : new UUID[]{event.receipt().readerId(), event.peerId()}) {
            try {
                messaging.convertAndSendToUser(user.toString(), "/queue/chat-reads", event.receipt());
            } catch (MessagingException exception) {
                // REST polling repairs a missed notification; committed state remains authoritative.
                log.warn("Chat read notification delivery failed");
            }
        }
    }
}
