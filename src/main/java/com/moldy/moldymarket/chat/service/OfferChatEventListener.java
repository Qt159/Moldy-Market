package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.entity.Message;
import com.moldy.moldymarket.chat.enums.ConversationStatus;
import com.moldy.moldymarket.chat.enums.MessageType;
import com.moldy.moldymarket.chat.mapper.MessageMapper;
import com.moldy.moldymarket.chat.repository.ConversationRepository;
import com.moldy.moldymarket.chat.repository.MessageRepository;
import com.moldy.moldymarket.offer.entity.Offer;
import com.moldy.moldymarket.offer.enums.OfferSide;
import com.moldy.moldymarket.offer.enums.OfferStatus;
import com.moldy.moldymarket.offer.event.OfferEvent;
import com.moldy.moldymarket.store.entity.Store;
import com.moldy.moldymarket.store.repository.StoreRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Mirrors Offer lifecycle events as structured Offer cards inside the buyer-seller Chat conversation
 * and broadcasts updates over STOMP so both participants see live state changes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OfferChatEventListener {
    ConversationRepository conversationRepository;
    MessageRepository messageRepository;
    MessageMapper messageMapper;
    StoreRepository storeRepository;
    SimpMessagingTemplate simpMessagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOfferEvent(OfferEvent event) {
        if (event == null || event.offer() == null || event.status() == null) return;
        try {
            handleOfferEvent(event);
        } catch (Exception ex) {
            log.warn("Could not mirror offer {} event {} to chat: {}",
                    event.offer().getId(), event.status(), ex.getMessage());
        }
    }

    private void handleOfferEvent(OfferEvent event) {
        Offer offer = event.offer();
        UUID buyerId = offer.getBuyerId();
        UUID sellerChatUserId = resolveSellerChatUserId(offer);
        if (buyerId == null || sellerChatUserId == null || buyerId.equals(sellerChatUserId)) {
            return;
        }

        Conversation conversation = conversationRepository.findPair(buyerId, sellerChatUserId).stream()
                .findFirst()
                .orElseGet(() -> {
                    Conversation created = new Conversation();
                    created.setParticipant1Id(buyerId);
                    created.setParticipant2Id(sellerChatUserId);
                    return conversationRepository.save(created);
                });

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            return;
        }

        String preview = buildPreview(event.status(), offer.getPrice());
        Message message;
        if (event.status() == OfferStatus.PENDING) {
            message = new Message();
            message.setConversation(conversation);
            message.setSenderId(offer.getSide() == OfferSide.BUYER ? buyerId : sellerChatUserId);
            message.setType(MessageType.TEXT);
            message.setContent(preview);
            message.setAttachedProductId(offer.getProductId());
            message.setOfferId(offer.getId());
        } else {
            message = messageRepository.findFirstByOfferIdOrderByIdDesc(offer.getId())
                    .orElseGet(() -> {
                        Message created = new Message();
                        created.setConversation(conversation);
                        created.setSenderId(offer.getSide() == OfferSide.BUYER ? buyerId : sellerChatUserId);
                        created.setType(MessageType.TEXT);
                        created.setAttachedProductId(offer.getProductId());
                        created.setOfferId(offer.getId());
                        return created;
                    });
            message.setContent(preview);
        }

        Message saved = messageRepository.save(message);
        conversation.setLastMessageAt(Instant.now());
        conversationRepository.save(conversation);

        MessageResponse payload = messageMapper.toMessageResponse(saved);
        simpMessagingTemplate.convertAndSendToUser(buyerId.toString(), "/queue/messages", payload);
        simpMessagingTemplate.convertAndSendToUser(sellerChatUserId.toString(), "/queue/messages", payload);
    }

    private UUID resolveSellerChatUserId(Offer offer) {
        if (offer.getStoreId() != null) {
            return storeRepository.findById(offer.getStoreId())
                    .map(Store::getOwnerId)
                    .orElse(offer.getSellerId());
        }
        return offer.getSellerId();
    }

    private String buildPreview(OfferStatus status, BigDecimal price) {
        String formatted = formatVnd(price);
        return switch (status) {
            case PENDING -> "Đề nghị trả giá " + formatted;
            case ACCEPTED -> "Đã đồng ý đề nghị " + formatted;
            case REJECTED -> "Đã từ chối đề nghị " + formatted;
            case CANCELLED -> "Đã rút đề nghị " + formatted;
            case EXPIRED -> "Đề nghị " + formatted + " đã hết hạn";
            case SUPERSEDED -> "Đã có mức giá đề nghị mới";
        };
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
        return format.format(amount) + " ₫";
    }
}
