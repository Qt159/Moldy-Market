package com.moldy.moldymarket.chat.config;

import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.security.user.CustomUserDetailsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final String SESSION_TOKEN = StompAuthInterceptor.class.getName() + ".token";
    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern SEND_DESTINATION =
            Pattern.compile("^/app/conversations/" + UUID_PATTERN + "/send$");
    static Pattern CONVERSATION_TOPIC =
            Pattern.compile("^/topic/conversations/(" + UUID_PATTERN + ")$");
    JwtService jwtService;
    CustomUserDetailsService customUserDetailsService;
    ConversationService conversationService;


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (accessor.getCommand() == StompCommand.CONNECT) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing bearer token");
            }
            String token = header.substring(7);
            UUID userId = validateToken(token);
            Map<String, Object> attributes = accessor.getSessionAttributes();
            if (attributes == null) throw new AccessDeniedException("Missing WebSocket session");
            attributes.put(SESSION_TOKEN, token);
            accessor.setUser(new StompPrincipal(userId.toString()));
        } else if (accessor.getCommand() == StompCommand.SEND || accessor.getCommand() == StompCommand.SUBSCRIBE) {
            UUID userId = requireAuthenticatedSession(accessor);
            String destination = accessor.getDestination();
            if (accessor.getCommand() == StompCommand.SEND) {
                if (destination == null || !SEND_DESTINATION.matcher(destination).matches()) {
                    throw new AccessDeniedException("Client may send only to the chat application");
                }
            } else {
                authorizeSubscribe(destination, userId);
            }
        }
        return message;
    }

    private UUID validateToken(String token) {
        try {
            if (!jwtService.isTokenValid(token))
                throw new AccessDeniedException("Token is invalid or expired");
            UUID userId = jwtService.extractUserId(token);
            UserDetails user = customUserDetailsService.loadUserById(userId);
            if(!user.isEnabled() || !user.isAccountNonLocked()) {
                throw new AccessDeniedException("Account is disabled or locked");
            }
            return userId;
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("Websocket authentication failed");
        }
    }

    private UUID requireAuthenticatedSession(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object storedToken = attributes == null ? null : attributes.get(SESSION_TOKEN);
        if (accessor.getUser() == null || !(storedToken instanceof String token)) {
            throw new AccessDeniedException("Not authenticated");
        }
        UUID userId = validateToken(token);
        if (!userId.toString().equals(accessor.getUser().getName())) {
            throw new AccessDeniedException("Session identity mismatch");
        }
        return userId;
    }



    private void authorizeSubscribe(String destination, UUID userId) {
        if ("/user/queue/messages".equals(destination) || "/user/queue/errors".equals(destination)
                || "/user/queue/chat-reads".equals(destination)) return;

        var topic = CONVERSATION_TOPIC.matcher(destination == null ? "" : destination);
        if (topic.matches() && conversationService.isParticipant(UUID.fromString(topic.group(1)), userId)) return;
        throw new AccessDeniedException("Subscription is not allowed");
    }
}
