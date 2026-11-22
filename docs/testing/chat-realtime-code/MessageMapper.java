package com.moldy.moldymarket.chat.mapper;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Message;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MessageMapper {
    Message toMessage(SendMessageRequest request);
    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "attachment", ignore = true)
    MessageResponse toMessageResponse(Message message);
}
