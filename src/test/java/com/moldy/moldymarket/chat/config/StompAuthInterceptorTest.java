package com.moldy.moldymarket.chat.config;

import com.moldy.moldymarket.chat.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StompAuthInterceptorTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final ConversationService conversations = mock(ConversationService.class);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(null, null, conversations);

    @Test
    void onlyAllowsClientsToSendThroughTheChatHandler() {
        Message<?> send = frame(StompCommand.SEND, "/app/conversations/" + conversationId + "/send", true);
        assertSame(send, interceptor.preSend(send, channel));

        Message<?> brokerSend = frame(StompCommand.SEND, "/topic/conversations/" + conversationId, true);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(brokerSend, channel));
    }

    @Test
    void deniesUnauthenticatedAndDirectQueueSubscriptions() {
        Message<?> anonymous = frame(StompCommand.SUBSCRIBE, "/user/queue/messages", false);
        Message<?> directQueue = frame(StompCommand.SUBSCRIBE, "/queue/messages-userother", true);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(anonymous, channel));
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(directQueue, channel));

        Message<?> ownQueue = frame(StompCommand.SUBSCRIBE, "/user/queue/messages", true);
        assertSame(ownQueue, interceptor.preSend(ownQueue, channel));
    }

    @Test
    void conversationTopicRequiresMembership() {
        Message<?> subscribe = frame(StompCommand.SUBSCRIBE, "/topic/conversations/" + conversationId, true);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(subscribe, channel));

        when(conversations.isParticipant(conversationId, userId)).thenReturn(true);
        assertSame(subscribe, interceptor.preSend(subscribe, channel));
    }

    private Message<?> frame(StompCommand command, String destination, boolean authenticated) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        if (authenticated) headers.setUser(new StompPrincipal(userId.toString()));
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
