package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.dto.request.StartConversationRequest;
import com.moldy.moldymarket.chat.dto.response.ConversationResponse;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.mapper.ConversationMapper;
import com.moldy.moldymarket.chat.mapper.MessageMapper;
import com.moldy.moldymarket.chat.repository.ConversationRepository;
import com.moldy.moldymarket.chat.repository.MessageRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationService {
    private final ConversationMapper conversationMapper;

    ConversationRepository conversationRepository;
    MessageRepository messageRepository;
    MessageMapper messageMapper;

    public ConversationResponse startOrGetConversation(UUID currentUserId, StartConversationRequest request){
        Conversation conversation = conversationRepository.findExistingConversation(currentUserId, request.targetUserId())
                .orElseGet(() -> {
                    Conversation newConversation = new Conversation();
                    newConversation.setParticipant1Id(currentUserId);
                    newConversation.setParticipant2Id(request.targetUserId());
                    return newConversation;
                });
        return conversationMapper.toConversationResponse(conversationRepository.save(conversation));
    }
    public List<ConversationResponse> getUserInbox(UUID currentUserId){
        return conversationRepository.findAllByUserId(currentUserId).stream().map(conversationMapper::toConversationResponse).toList();
    }

    public Page<MessageResponse> getChatHistory(UUID currentUserId, UUID conversationId, Pageable pageable){
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));

        //only participants can view history
        if(!conversation.getParticipant1Id().equals(currentUserId) && !conversation.getParticipant2Id().equals(currentUserId))
            throw new AppException(ErrorCode.SENDER_NOT_IN_CONVERSATION);

        return messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable)
                .map(messageMapper::toMessageResponse);
    }
}
