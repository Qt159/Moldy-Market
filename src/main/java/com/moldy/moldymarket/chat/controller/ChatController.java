package com.moldy.moldymarket.chat.controller;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.service.MessageService;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@Tag(name = "Tin nhắn (Chat Messages)", description = "Xử lý gửi nhận tin nhắn thời gian thực qua WebSocket STOMP")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-messages")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatController {
    MessageService messageService;
    SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/conversations/{conversationId}/send")
    public void sendMessage(
            @DestinationVariable UUID conversationId,
            @Payload SendMessageRequest request,
            Principal principal
            ){
        //get sender id from principal

        UUID senderId = UUID.fromString(principal.getName());

        MessageResponse savedMessage = messageService.saveMessage(conversationId, senderId, request);

        UUID recipientId = messageService.getRecipientId(conversationId, senderId);

        simpMessagingTemplate.convertAndSendToUser(recipientId.toString(),
                "/queue/messages",
                savedMessage);

        simpMessagingTemplate.convertAndSendToUser(
                senderId.toString(),
                "/queue/messages",
                savedMessage);
    }

}
