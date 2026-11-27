package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.entity.Message;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfferChatEventListenerTest {

    @Mock ConversationRepository conversationRepository;
    @Mock MessageRepository messageRepository;
    @Mock MessageMapper messageMapper;
    @Mock StoreRepository storeRepository;
    @Mock SimpMessagingTemplate simpMessagingTemplate;

    @InjectMocks OfferChatEventListener listener;

    @Test
    void createsStructuredOfferMessageAndBroadcastsToBothParticipantsOnPending() {
        UUID buyerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        Offer offer = Offer.builder()
                .productId(productId)
                .buyerId(buyerId)
                .sellerId(UUID.randomUUID())
                .storeId(storeId)
                .originalPrice(BigDecimal.valueOf(1000000))
                .price(BigDecimal.valueOf(850000))
                .side(OfferSide.BUYER)
                .negotiationId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        ReflectionTestUtils.setField(offer, "id", offerId);

        Store store = mock(Store.class);
        when(store.getOwnerId()).thenReturn(ownerId);
        when(storeRepository.findById(storeId)).thenReturn(Optional.of(store));

        Conversation room = new Conversation();
        ReflectionTestUtils.setField(room, "id", conversationId);
        room.setParticipant1Id(buyerId);
        room.setParticipant2Id(ownerId);
        when(conversationRepository.findPair(buyerId, ownerId)).thenReturn(List.of(room));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse payload = MessageResponse.builder()
                .id(10L)
                .conversationId(conversationId)
                .senderId(buyerId)
                .type(MessageType.TEXT)
                .offerId(offerId)
                .attachedProductId(productId)
                .build();
        when(messageMapper.toMessageResponse(any(Message.class))).thenReturn(payload);

        listener.onOfferEvent(OfferEvent.builder()
                .status(OfferStatus.PENDING)
                .offer(offer)
                .recipientId(ownerId)
                .build());

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getOfferId()).isEqualTo(offerId);
        assertThat(captor.getValue().getAttachedProductId()).isEqualTo(productId);
        assertThat(captor.getValue().getSenderId()).isEqualTo(buyerId);
        assertThat(captor.getValue().getType()).isEqualTo(MessageType.TEXT);

        verify(simpMessagingTemplate).convertAndSendToUser(eq(buyerId.toString()), eq("/queue/messages"), eq(payload));
        verify(simpMessagingTemplate).convertAndSendToUser(eq(ownerId.toString()), eq("/queue/messages"), eq(payload));
    }

    @Test
    void updatesExistingOfferMessagePreviewAndBroadcastsOnAccepted() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        Offer offer = Offer.builder()
                .productId(productId)
                .buyerId(buyerId)
                .sellerId(sellerId)
                .storeId(null)
                .originalPrice(BigDecimal.valueOf(1000000))
                .price(BigDecimal.valueOf(900000))
                .side(OfferSide.BUYER)
                .negotiationId(UUID.randomUUID())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        ReflectionTestUtils.setField(offer, "id", offerId);

        Conversation room = new Conversation();
        ReflectionTestUtils.setField(room, "id", conversationId);
        room.setParticipant1Id(buyerId);
        room.setParticipant2Id(sellerId);
        when(conversationRepository.findPair(buyerId, sellerId)).thenReturn(List.of(room));

        Message existing = new Message();
        existing.setConversation(room);
        existing.setSenderId(buyerId);
        existing.setType(MessageType.TEXT);
        existing.setOfferId(offerId);
        existing.setAttachedProductId(productId);
        when(messageRepository.findFirstByOfferIdOrderByIdDesc(offerId)).thenReturn(Optional.of(existing));
        when(messageRepository.save(existing)).thenReturn(existing);
        when(conversationRepository.save(room)).thenReturn(room);

        MessageResponse payload = MessageResponse.builder()
                .id(11L)
                .conversationId(conversationId)
                .senderId(buyerId)
                .type(MessageType.TEXT)
                .offerId(offerId)
                .build();
        when(messageMapper.toMessageResponse(existing)).thenReturn(payload);

        listener.onOfferEvent(OfferEvent.builder()
                .status(OfferStatus.ACCEPTED)
                .offer(offer)
                .recipientId(buyerId)
                .build());

        assertThat(existing.getContent()).contains("Đã đồng ý đề nghị");
        verify(simpMessagingTemplate).convertAndSendToUser(eq(buyerId.toString()), eq("/queue/messages"), eq(payload));
        verify(simpMessagingTemplate).convertAndSendToUser(eq(sellerId.toString()), eq("/queue/messages"), eq(payload));
    }
}
