package com.moldy.moldymarket.chat.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ChatReadReceipt(UUID conversationId, UUID readerId, Long messageId, Instant readAt) {}
