package com.moldy.moldymarket.chat.config;


import com.moldy.moldymarket.security.jwt.JwtService;
import com.moldy.moldymarket.security.user.CustomUserDetailsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.UUID;

@Configuration
@Slf4j
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    JwtService jwtService;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry){
        //prefix for topics (server-to-client)
        registry.enableSimpleBroker("/topic", "/queue");
        //prefix for client-to-server messages
        registry.setApplicationDestinationPrefixes("/app");
        //for private user
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration){
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public @Nullable Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if(accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())){
                    String authHeader = accessor.getFirstNativeHeader("Authorization");

                    if(authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new IllegalArgumentException("No bearer token found in CONNECT headers!");
                    }

                    String token = authHeader.substring(7);

                    try{
                        if(jwtService.isTokenValid(token)){
                            UUID userId = jwtService.extractUserId(token);

                            accessor.setUser(new StompPrincipal(userId.toString()));
                            log.info("Successfully authenticated WebSocket connection for user: {}", userId);
                        }
                        else{
                            log.error("Token validation failed for token: {}", token);
                            throw new IllegalArgumentException("Token is invalid or expired");
                        }
                    }catch (Exception e){
                        log.error("Critical auth error in WebSocket: ", e);
                        throw new IllegalArgumentException("Authentication failed: " + e.getMessage());
                    }
                }
                return message;
            }
        });
    }

}
