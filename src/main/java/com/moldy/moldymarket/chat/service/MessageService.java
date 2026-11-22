package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.entity.Message;
import com.moldy.moldymarket.chat.enums.ConversationStatus;
import com.moldy.moldymarket.chat.enums.MessageType;
import com.moldy.moldymarket.chat.mapper.MessageMapper;
import com.moldy.moldymarket.chat.repository.ConversationRepository;
import com.moldy.moldymarket.chat.repository.MessageRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageService {
    MessageRepository messageRepository;
    ConversationRepository conversationRepository;
    MessageMapper messageMapper;
    ChatAccessPolicy accessPolicy;

    @Transactional
    public MessageResponse saveMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        // Serialize sends in the same conversation so lastMessageAt cannot move backwards.
        Conversation conversation = conversationRepository.findForMessage(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
        boolean isUserParticipant = senderId.equals(conversation.getParticipant1Id()) ||
                senderId.equals(conversation.getParticipant2Id());

        if(!isUserParticipant) throw new AppException(ErrorCode.SENDER_NOT_IN_CONVERSATION);

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Conversation is closed");
        }
        if (request == null || request.type() == null|| (request.type() == MessageType.TEXT
        && (request.content() == null || request.content().isBlank()))) {
            throw  new AppException(ErrorCode.INVALID_REQUEST, "Message content is required");
        }

        accessPolicy.requireCanSend(conversation.getParticipant1Id(), conversation.getParticipant2Id());

        Message message = messageMapper.toMessage(request);
        message.setConversation(conversation);
        message.setSenderId(senderId);

        Message saved = messageRepository.save(message);
        conversation.setLastMessageAt(saved.getSentAt());
        return messageMapper.toMessageResponse(saved);
    }

    public UUID getRecipientId(UUID conversationId, UUID senderId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));

        if(senderId.equals(conversation.getParticipant1Id())){
            return conversation.getParticipant2Id();
        }
        else if(senderId.equals(conversation.getParticipant2Id())){
            return conversation.getParticipant1Id();
        }
        else throw new AppException(ErrorCode.SENDER_NOT_IN_CONVERSATION);

    }
}
