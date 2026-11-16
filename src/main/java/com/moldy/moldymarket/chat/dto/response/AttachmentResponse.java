package com.moldy.moldymarket.chat.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AttachmentResponse(
        UUID productId,
        String productName,
        String productImageUrl,
        Double price
) {

}
