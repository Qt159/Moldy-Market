package com.moldy.moldymarket.chat.service;

import com.moldy.moldymarket.chat.entity.Conversation;
import com.moldy.moldymarket.chat.enums.ConversationStatus;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.store.repository.StoreStaffRepository;
import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.user.entity.UserStatus;
import com.moldy.moldymarket.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class ChatAccessPolicy {
    private final UserRepository userRepository;
    private final StoreStaffRepository staffRepository;

    public Participants load(Collection<UUID> userIds) {
        Set<UUID> ids = new HashSet<>(userIds);
        Map<UUID, User> users = new HashMap<>();
        Map<UUID, Set<UUID>> employerOwners = new HashMap<>();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> users.put(user.getId(), user));
            staffRepository.findAllByUserIdIn(ids).forEach(staff ->
                    employerOwners.computeIfAbsent(staff.getUser().getId(), id -> new HashSet<>())
                            .add(staff.getStore().getOwnerId()));
        }
        return new Participants(users, employerOwners);
    }

    public void requireCanSend(UUID firstId, UUID secondId) {
        if (firstId.equals(secondId)) throw new AppException(ErrorCode.CHAT_INVALID_PARTICIPANTS);
        ErrorCode error = load(Set.of(firstId, secondId)).denialFor(firstId, secondId);
        if (error != null) throw new AppException(error);
    }

    public record Participants(Map<UUID, User> users, Map<UUID, Set<UUID>> employerOwners) {
        public String nameFor(UUID id) {
            User user = users.get(id);
            return user == null || user.getFullName() == null ? null : user.getFullName().trim();
        }

        public ErrorCode denialFor(Conversation room) {
            return room.getStatus() != ConversationStatus.ACTIVE ? ErrorCode.INVALID_REQUEST
                    : denialFor(room.getParticipant1Id(), room.getParticipant2Id());
        }

        public String blockedReasonFor(Conversation room) {
            ErrorCode error = denialFor(room);
            return error == null ? null : error.name();
        }

        public ErrorCode denialFor(UUID firstId, UUID secondId) {
            if (firstId.equals(secondId)) return ErrorCode.CHAT_INVALID_PARTICIPANTS;
            User first = users.get(firstId), second = users.get(secondId);
            if (first == null || second == null || first.getStatus() != UserStatus.ACTIVE || second.getStatus() != UserStatus.ACTIVE) {
                return ErrorCode.CHAT_PARTICIPANT_UNAVAILABLE;
            }
            if (employerOwners.getOrDefault(firstId, Set.of()).contains(secondId) ||
                    employerOwners.getOrDefault(secondId, Set.of()).contains(firstId)) {
                return ErrorCode.CHAT_STORE_MEMBER;
            }
            return null;
        }
    }
}
