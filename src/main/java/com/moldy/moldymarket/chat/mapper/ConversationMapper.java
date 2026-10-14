package com.moldy.moldymarket.chat.mapper;

import com.moldy.moldymarket.chat.dto.response.ConversationResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationMapper {
    ConversationResponse toConversationResponse(Conversation conversation);
}
