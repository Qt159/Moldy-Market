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
import org.springframework.context.ApplicationEventPublisher;
import com.moldy.moldymarket.chat.dto.response.ChatReadReceipt;
import java.util.stream.Collectors;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.moldy.moldymarket.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationService {
    private final ConversationMapper conversationMapper;

    ConversationRepository conversationRepository;
    MessageRepository messageRepository;
    MessageMapper messageMapper;
    UserRepository userRepository;
    ChatAccessPolicy accessPolicy;
    ApplicationEventPublisher events;

    @Transactional
    public ConversationResponse startOrGetConversation(UUID currentUserId, StartConversationRequest request) {
        if(currentUserId.equals(request.targetUserId())) throw new AppException(ErrorCode.CHAT_INVALID_PARTICIPANTS);
        // Lock both accounts in the same order to serialize A->B and B->A creation.
        Set<UUID> pair = Set.of(currentUserId, request.targetUserId());
        pair.stream().sorted().forEach(id -> userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));
        accessPolicy.requireCanSend(currentUserId, request.targetUserId());
        Conversation conversation = conversationRepository.findPair(currentUserId, request.targetUserId()).stream()
                .findFirst().orElseGet(() -> {
                    Conversation newConversation = new Conversation();
                    newConversation.setParticipant1Id(currentUserId);
                    newConversation.setParticipant2Id(request.targetUserId());
                    return newConversation;
                });
        return toResponse(conversationRepository.save(conversation), currentUserId);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationForUser(UUID conversationId, UUID userId){
        Conversation conversation = getOrThrow(conversationId);
        assertParticipant(conversation, userId);
        return toResponse(conversation, userId);
    }

    @Transactional(readOnly = true)
    public boolean isParticipant(UUID conversationId, UUID userId) {
        return conversationRepository.existsByIdAndParticipant(conversationId, userId);
    }


    @Transactional(readOnly = true)
    public List<ConversationResponse> getUserConversationList(UUID currentUserId){
        List<Conversation> rooms = conversationRepository.findAllByUserId(currentUserId);
        Set<UUID> ids = new HashSet<>();
        rooms.forEach(room -> { ids.add(room.getParticipant1Id()); ids.add(room.getParticipant2Id()); });
        var participants = accessPolicy.load(ids);
        var counts = messageRepository.countUnreadForUser(currentUserId).stream().collect(Collectors.toMap(
                MessageRepository.UnreadCount::getConversationId, MessageRepository.UnreadCount::getUnreadCount));
        return rooms.stream().map(room -> conversationMapper.toConversationResponse(room, participants).toBuilder()
                .unreadCount(counts.getOrDefault(room.getId(), 0L)).build()).toList();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getChatHistory(UUID currentUserId, UUID conversationId, Pageable pageable){
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));

        //only participants can view history
        if(!conversation.getParticipant1Id().equals(currentUserId) && !conversation.getParticipant2Id().equals(currentUserId))
            throw new AppException(ErrorCode.SENDER_NOT_IN_CONVERSATION);

        return messageRepository.findByConversationIdOrderBySentAtDesc(conversationId, pageable)
                .map(messageMapper::toMessageResponse);
    }

    /** Acknowledge only the displayed prefix; concurrent later messages stay unread. */
    @Transactional
    public void markAsRead(UUID conversationId, UUID userId, Long messageId) {
        Conversation conversation = conversationRepository.findForMessage(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
        assertParticipant(conversation, userId);
        var message = messageRepository.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST));
        boolean first = conversation.getParticipant1Id().equals(userId);
        Long previous = first ? conversation.getLastReadMessageIdParticipant1() : conversation.getLastReadMessageIdParticipant2();
        Instant legacy = first ? conversation.getLastReadAtParticipant1() : conversation.getLastReadAtParticipant2();
        long next = message.getId();
        if (previous == null && legacy != null) {
            next = Math.max(next, messageRepository.findFirstByConversationIdAndSentAtLessThanEqualOrderByIdDesc(conversationId, legacy)
                    .map(com.moldy.moldymarket.chat.entity.Message::getId).orElse(0L));
        }
        if (previous != null && previous >= next) return;
        Instant now = Instant.now();
        if (first) {
            conversation.setLastReadMessageIdParticipant1(next);
            conversation.setLastReadAtParticipant1(now);
        } else {
            conversation.setLastReadMessageIdParticipant2(next);
            conversation.setLastReadAtParticipant2(now);
        }
        conversationRepository.save(conversation);
        events.publishEvent(new ChatReadNotifier.ReadCommitted(new ChatReadReceipt(conversationId, userId, next, now),
                first ? conversation.getParticipant2Id() : conversation.getParticipant1Id()));
    }

    void assertParticipant(Conversation conversation, UUID userId) {
        if (!conversation.getParticipant1Id().equals(userId) && !conversation.getParticipant2Id().equals(userId)) {
            throw new AppException(ErrorCode.CHAT_NOT_PARTICIPANT);
        }
    }


    private ConversationResponse toResponse(Conversation room, UUID userId) {
        long count = messageRepository.countUnreadForUser(userId).stream()
                .filter(row -> row.getConversationId().equals(room.getId())).mapToLong(MessageRepository.UnreadCount::getUnreadCount)
                .findFirst().orElse(0L);
        return conversationMapper.toConversationResponse(room, accessPolicy.load(
                Set.of(room.getParticipant1Id(), room.getParticipant2Id()))).toBuilder().unreadCount(count).build();
    }

    private Conversation getOrThrow(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_EXISTED));
    }
}
