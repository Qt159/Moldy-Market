package com.moldy.moldymarket.chat.controller;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.ChatErrorResponse;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.service.MessageService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

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
            @Payload @Valid SendMessageRequest request,
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

    @MessageExceptionHandler(AppException.class)
    @SendToUser("/queue/errors")
    public ChatErrorResponse handle(AppException e) {
        return new ChatErrorResponse(e.getErrorCode().getCode(), e.getMessage(), Instant.now());
    }
}
