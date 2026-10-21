package com.moldy.moldymarket.chat.mapper;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageMapper {
    Message toMessage(SendMessageRequest request);
    MessageResponse toMessageResponse(Message message);
}
