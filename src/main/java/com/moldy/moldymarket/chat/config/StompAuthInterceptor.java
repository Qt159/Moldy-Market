package com.moldy.moldymarket.chat.config;

import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.security.user.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final String CONVERSATION_ID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile("^/topic/conversations/(" + CONVERSATION_ID + ")$");
    private static final Pattern SEND_MESSAGE = Pattern.compile("^/app/conversations/" + CONVERSATION_ID + "/send$");
    private static final Set<String> USER_QUEUES = Set.of("/user/queue/messages", "/user/queue/errors");
    JwtService jwtService;
    CustomUserDetailsService customUserDetailsService;
    ConversationService conversationService;


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel){
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> {
                requirePrincipal(accessor);
                String destination = accessor.getDestination();
                if (destination == null || !SEND_MESSAGE.matcher(destination).matches()) {
                    throw new AccessDeniedException("Unsupported chat destination");
                }
            }
            default -> {}
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if(authHeader == null || !authHeader.startsWith("Bearer ")){
            throw new AccessDeniedException("Missing bearer token in CONNECT headers");
        }
        String token = authHeader.substring(7);
        UUID userId;
        try{
            if(!jwtService.isTokenValid(token)){
                throw new AccessDeniedException("Token is invalid or expired");
            }
            userId = jwtService.extractUserId(token);
        }catch (JwtException | IllegalArgumentException e){
            log.warn("WebSocket auth rejected");
            throw new AccessDeniedException("Authentication failed");
        }

        UserDetails user = customUserDetailsService.loadUserById(userId);
        if(!user.isEnabled() || !user.isAccountNonLocked()){
            throw new AccessDeniedException("Account is disabled or locked");
        }

        accessor.setUser(new StompPrincipal(userId.toString()));
        log.info("Websocket connected for user {}", userId);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Principal principal = requirePrincipal(accessor);
        String destination = accessor.getDestination();
        if (destination != null && USER_QUEUES.contains(destination)) return;

        Matcher topic = CONVERSATION_TOPIC.matcher(destination == null ? "" : destination);
        if (!topic.matches()) {
            throw new AccessDeniedException("Unsupported chat subscription");
        }
        UUID userId = UUID.fromString(principal.getName());
        UUID conversationId = UUID.fromString(topic.group(1));
        if (!conversationService.isParticipant(conversationId, userId)) {
            throw new AccessDeniedException("You are not a participant of this conversation");
        }
    }

    private Principal requirePrincipal(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) throw new AccessDeniedException("Not authenticated");
        return principal;
    }
}
