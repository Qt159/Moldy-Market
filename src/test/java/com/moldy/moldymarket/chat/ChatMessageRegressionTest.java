package com.moldy.moldymarket.chat;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.entity.*;
import com.moldy.moldymarket.chat.enums.*;
import com.moldy.moldymarket.chat.mapper.MessageMapper;
import com.moldy.moldymarket.chat.repository.*;
import com.moldy.moldymarket.chat.service.MessageService;
import com.moldy.moldymarket.chat.service.ChatAccessPolicy;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.user.entity.*;
import com.moldy.moldymarket.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMessageRegressionTest {
    ConversationRepository conversations;
    MessageRepository messages;
    ChatAccessPolicy policy;
    MessageMapper mapper = Mappers.getMapper(MessageMapper.class);
    MessageService service;
    UUID roomId = UUID.randomUUID(), senderId = UUID.randomUUID();
    Conversation room;

    @BeforeEach void setup() {
        conversations = mock(ConversationRepository.class);
        messages = mock(MessageRepository.class); policy = mock(ChatAccessPolicy.class);
        service = new MessageService(messages, conversations, mapper, policy);
        room = new Conversation(); ReflectionTestUtils.setField(room, "id", roomId);
        room.setParticipant1Id(senderId); room.setParticipant2Id(UUID.randomUUID());
        room.setLastMessageAt(Instant.EPOCH);
        when(conversations.findForMessage(roomId)).thenReturn(Optional.of(room));
        var sender = new User(); sender.setId(senderId); sender.setStatus(UserStatus.ACTIVE);
        lenient().when(messages.save(any())).thenAnswer(invocation -> {
            Message value = invocation.getArgument(0); ReflectionTestUtils.setField(value, "id", 42L); return value;
        });
    }

    @Test void responseIncludesTheRealConversationAndUpdatesInboxTimestamp() {
        var saved = service.saveMessage(roomId, senderId, new SendMessageRequest(MessageType.TEXT, "Xin chào", null));
        assertThat(saved.conversationId()).isEqualTo(roomId);
        assertThat(saved.id()).isEqualTo(42L);
        assertThat(room.getLastMessageAt()).isEqualTo(saved.sentAt());
        verify(messages, times(1)).save(any());
        verify(conversations).findForMessage(roomId);
    }

    @Test void closedConversationDoesNotPersistMessages() {
        room.setStatus(ConversationStatus.CLOSED);
        assertThatThrownBy(() -> service.saveMessage(roomId, senderId,
                new SendMessageRequest(MessageType.TEXT, "Hello", null))).isInstanceOf(AppException.class);
        verifyNoInteractions(messages);
    }

    @Test void blankTextDoesNotPersistMessages() {
        assertThatThrownBy(() -> service.saveMessage(roomId, senderId,
                new SendMessageRequest(MessageType.TEXT, " \n\t", null))).isInstanceOf(AppException.class);
        verifyNoInteractions(messages);
    }

    @Test void nonParticipantCannotSend() {
        room.setParticipant1Id(UUID.randomUUID());
        assertThatThrownBy(() -> service.saveMessage(roomId, senderId,
                new SendMessageRequest(MessageType.TEXT, "Hello", null))).isInstanceOf(AppException.class);
        verifyNoInteractions(messages);
    }
}
