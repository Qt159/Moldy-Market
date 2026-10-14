package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.dto.request.SendMessageRequest;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.entity.Message;
import com.moldy.moldymarket.chat.mapper.MessageMapper;
import com.moldy.moldymarket.chat.repository.ConversationRepository;
import com.moldy.moldymarket.chat.repository.MessageRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MessageService {
    MessageRepository messageRepository;
    ConversationRepository conversationRepository;
    UserRepository userRepository;
    MessageMapper messageMapper;


    public MessageResponse saveMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean isUserParticipant = sender.getId().equals(conversation.getParticipant1Id()) || sender.getId().equals(conversation.getParticipant2Id());

        if(!isUserParticipant) throw new AppException(ErrorCode.SENDER_NOT_IN_CONVERSATION);

        Message message = messageMapper.toMessage(request);
        message.setConversation(conversation);
        message.setSenderId(sender.getId());

        return messageMapper.toMessageResponse(messageRepository.save(message));
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
