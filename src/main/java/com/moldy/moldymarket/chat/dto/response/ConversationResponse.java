package com.moldy.moldymarket.chat.dto.response;

import com.moldy.moldymarket.chat.enums.ConversationStatus;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record ConversationResponse(
        UUID id,
        UUID participant1Id,
        UUID participant2Id,
        ConversationStatus status,
        Instant lastMessageAt,
        Instant lastReadAtParticipant1,
        Instant lastReadAtParticipant2,
        Instant createdAt
) {
}
