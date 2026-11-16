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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.file.AccessDeniedException;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StompAuthInterceptor implements ChannelInterceptor {
    static Pattern CONVERSATION_TOPIC =
            Pattern.compile("^/topic/conversations/([0-9a-fA-F-]{36})$");
    JwtService jwtService;
    CustomUserDetailsService customUserDetailsService;
    ConversationService conversationService;


    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel){
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if(accessor == null) return message;

        if(StompCommand.CONNECT.equals(accessor.getCommand())){
            try {
                authenticate(accessor);
            } catch (AccessDeniedException e) {
                throw new RuntimeException(e);
            }
        }
        else if(StompCommand.SUBSCRIBE.equals(accessor.getCommand())){
            try {
                authorizeSubscribe(accessor);
            } catch (AccessDeniedException e) {
                throw new RuntimeException(e);
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) throws AccessDeniedException {
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
            log.warn("Websocket auth rejected: {}", e.getMessage());
            throw new AccessDeniedException("Authentication failed");
        }

        UserDetails user = customUserDetailsService.loadUserById(userId);
        if(!user.isEnabled() || !user.isAccountNonLocked()){
            throw new AccessDeniedException("Account is disabled or locked");
        }

        accessor.setUser(new StompPrincipal(userId.toString()));
        log.info("Websocket connected for user {}", userId);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) throws AccessDeniedException {
        Matcher m = CONVERSATION_TOPIC.matcher(accessor.getDestination() == null ? "" : accessor.getDestination());
        if(!m.matches()) return;

        Principal principal = accessor.getUser();
        if(principal == null) throw new AccessDeniedException("Not authenticated");
        UUID userId = UUID.fromString(principal.getName());
        UUID conversationId = UUID.fromString(m.group(1));
        if(!conversationService.isParticipant(conversationId, userId)){
            throw new AccessDeniedException("You are not a participant of this conversation");
        }
    }
}
