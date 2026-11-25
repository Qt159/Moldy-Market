package com.moldy.moldymarket.chat.mapper;

import com.moldy.moldymarket.chat.dto.response.ConversationResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Context;
import com.moldy.moldymarket.chat.service.ChatAccessPolicy;

@Mapper(componentModel = "spring")
public interface ConversationMapper {
    @Mapping(target = "participant1Name", expression = "java(participants.nameFor(conversation.getParticipant1Id()))")
    @Mapping(target = "participant2Name", expression = "java(participants.nameFor(conversation.getParticipant2Id()))")
    @Mapping(target = "canSend", expression = "java(participants.denialFor(conversation) == null)")
    @Mapping(target = "sendBlockedReason", expression = "java(participants.blockedReasonFor(conversation))")
    @Mapping(target = "unreadCount", ignore = true)
    @Mapping(target = "lastMessageContent", ignore = true)
    ConversationResponse toConversationResponse(Conversation conversation, @Context ChatAccessPolicy.Participants participants);
}
