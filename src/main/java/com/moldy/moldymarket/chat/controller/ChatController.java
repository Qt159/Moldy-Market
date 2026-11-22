package com.moldy.moldymarket.chat.controller;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.ChatErrorResponse;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.service.MessageService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Tag(name = "Tin nhắn (Chat Messages)", description = "Xử lý gửi nhận tin nhắn thời gian thực qua WebSocket STOMP")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-messages")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ChatController {
    MessageService messageService;
    SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/conversations/{conversationId}/send")
    public void sendMessage(
            @DestinationVariable UUID conversationId,
            @Payload @Valid SendMessageRequest request,
            Principal principal
            ){
        if (principal == null) throw new AppException(ErrorCode.UNAUTHORIZED);

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
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorResponse handle(AppException e) {
        return new ChatErrorResponse(e.getErrorCode().getCode(), e.getMessage(), Instant.now());
    }

    @MessageExceptionHandler({MethodArgumentNotValidException.class, MessageConversionException.class})
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleInvalidMessage(Exception exception) {
        return error(ErrorCode.INVALID_REQUEST);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleUnexpected(Exception exception) {
        log.error("Chat message processing failed: {}", exception.getClass().getName());
        return error(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ChatErrorResponse error(ErrorCode code) {
        return new ChatErrorResponse(code.getCode(), code.getMessage(), Instant.now());
    }
}
