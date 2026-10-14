package com.moldy.moldymarket.chat.dto.request;


import com.moldy.moldymarket.chat.enums.MessageType;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(
        @NotNull MessageType type,
        String content,
        String attachmentUrl
        ) {

}
