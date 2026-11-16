package com.moldy.moldymarket.chat.dto.response;

import com.moldy.moldymarket.chat.enums.MessageType;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record MessageResponse (
        Long id,
        UUID conversationId,
        UUID senderId,
        MessageType type,
        String content,
        AttachmentResponse attachment,
        Instant sentAt){
}
