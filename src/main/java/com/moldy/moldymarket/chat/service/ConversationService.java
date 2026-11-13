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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    @Transactional
    public ConversationResponse startOrGetConversation(UUID currentUserId, StartConversationRequest request) {
        if(currentUserId.equals(request.targetUserId())) throw new AppException(ErrorCode.CHAT_INVALID_PARTICIPANTS);
        Conversation conversation = conversationRepository.findByParticipant1IdAndParticipant2Id(currentUserId, request.targetUserId())
                .orElseGet(() -> {
                    Conversation newConversation = new Conversation();
                    newConversation.setParticipant1Id(currentUserId);
                    newConversation.setParticipant2Id(request.targetUserId());
                    return newConversation;
                });
        return conversationMapper.toConversationResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationForUser(UUID conversationId, UUID userId){
        Conversation conversation = getOrThrow(conversationId);
        assertParticipant(conversation, userId);
        return conversationMapper.toConversationResponse(conversation);
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return conversationRepository.existsByIdAndParticipant(conversationId, userId);
    }


    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversationList(UUID currentUserId){
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

    /** Đánh dấu đã đọc tới thời điểm hiện tại, cho đúng phía (buyer hoặc seller) của caller. */
    @Transactional
    public void markAsRead(UUID conversationId, UUID userId) {
        Conversation conversation = getOrThrow(conversationId);
        assertParticipant(conversation, userId);

        Instant now = Instant.now();
        if (conversation.getParticipant1Id().equals(userId)) {
            conversation.setLastReadAtParticipant1(now);
        } else {
            conversation.setLastReadAtParticipant2(now);
        }
        conversationRepository.save(conversation);
    }

    void assertParticipant(Conversation conversation, UUID userId) {
        if (!conversation.getParticipant1Id().equals(userId) && !conversation.getParticipant2Id().equals(userId)) {
            throw new AppException(ErrorCode.CHAT_NOT_PARTICIPANT);
        }
    }


    private Conversation getOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
    }
}
