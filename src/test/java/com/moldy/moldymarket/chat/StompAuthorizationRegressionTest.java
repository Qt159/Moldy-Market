package com.moldy.moldymarket.chat;

import com.moldy.moldymarket.chat.config.*;
import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.security.user.CustomUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompAuthorizationRegressionTest {
    JwtService jwt = mock(JwtService.class);
    CustomUserDetailsService users = mock(CustomUserDetailsService.class);
    ConversationService rooms = mock(ConversationService.class);
    StompAuthInterceptor interceptor = new StompAuthInterceptor(jwt, users, rooms);
    UUID userId = UUID.randomUUID();
    Map<String, Object> attributes;

    @BeforeEach void connect() {
        when(jwt.isTokenValid("test-token")).thenReturn(true);
        when(jwt.extractUserId("test-token")).thenReturn(userId);
        var user = mock(com.moldy.moldymarket.security.user.CustomUserDetails.class);
        when(user.isEnabled()).thenReturn(true); when(user.isAccountNonLocked()).thenReturn(true);
        when(users.loadUserById(userId)).thenReturn(user);
        attributes = new HashMap<>();
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setSessionAttributes(attributes); headers.setNativeHeader("Authorization", "Bearer test-token");
        headers.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
        assertThat(headers.getUser().getName()).isEqualTo(userId.toString());
    }

    private org.springframework.messaging.Message<byte[]> frame(StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionAttributes(attributes); headers.setUser(new StompPrincipal(userId.toString()));
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test void directBrokerPublishesAreRejected() {
        for (String destination : List.of("/queue/messages", "/queue/chat-reads", "/user/queue/chat-reads", "/topic/conversations/" + UUID.randomUUID(), "/user/another/queue/messages")) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, destination), null)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test void subscribingToRawOrAnotherUsersQueueIsRejected() {
        for (String destination : List.of("/queue/messages", "/queue/chat-reads", "/queue/messages-user-victim", "/user/another/queue/chat-reads", "/user/another/queue/messages", "/topic/other")) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination), null)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test void authenticatedSessionCanSubscribeToItsOwnQueuesAndSendToApplication() {
        for (String destination : List.of("/user/queue/messages", "/user/queue/errors", "/user/queue/chat-reads")) {
            assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination), null)).doesNotThrowAnyException();
        }
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SEND,
                "/app/conversations/" + UUID.randomUUID() + "/send"), null)).doesNotThrowAnyException();
    }

    @Test void topicRequiresMembership() {
        UUID roomId = UUID.randomUUID();
        String topic = "/topic/conversations/" + roomId;
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, topic), null)).isInstanceOf(RuntimeException.class);
        when(rooms.isParticipant(roomId, userId)).thenReturn(true);
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, topic), null)).doesNotThrowAnyException();
    }

    @Test void expiredTokenIsRejectedOnAnAlreadyConnectedSession() {
        when(jwt.isTokenValid("test-token")).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND,
                "/app/conversations/" + UUID.randomUUID() + "/send"), null)).isInstanceOf(RuntimeException.class);
    }
}
