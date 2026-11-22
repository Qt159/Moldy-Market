package com.moldy.moldymarket.chat.repository;

import com.moldy.moldymarket.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByConversationIdOrderBySentAtDesc(UUID conversationId, Pageable pageable);

    Optional<Message> findByIdAndConversationId(Long id, UUID conversationId);
    Optional<Message> findFirstByConversationIdAndSentAtLessThanEqualOrderByIdDesc(UUID conversationId, Instant readAt);

    interface UnreadCount {
        UUID getConversationId();
        long getUnreadCount();
    }

    @Query("""
            select c.id as conversationId, count(m) as unreadCount
            from Message m join m.conversation c
            where (
              (c.participant1Id = :userId and m.senderId = c.participant2Id and (
                (c.lastReadMessageIdParticipant1 is not null and m.id > c.lastReadMessageIdParticipant1) or
                (c.lastReadMessageIdParticipant1 is null and (c.lastReadAtParticipant1 is null or m.sentAt > c.lastReadAtParticipant1)))) or
              (c.participant2Id = :userId and m.senderId = c.participant1Id and (
                (c.lastReadMessageIdParticipant2 is not null and m.id > c.lastReadMessageIdParticipant2) or
                (c.lastReadMessageIdParticipant2 is null and (c.lastReadAtParticipant2 is null or m.sentAt > c.lastReadAtParticipant2)))))
            group by c.id
            """)
    List<UnreadCount> countUnreadForUser(@Param("userId") UUID userId);
}
