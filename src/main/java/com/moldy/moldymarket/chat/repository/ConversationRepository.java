package com.moldy.moldymarket.chat.repository;

import com.moldy.moldymarket.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    //finds if a chat already existed between two people, regardless of who started it
    @Query("select c from Conversation c where (c.participant1Id = :user1Id and c.participant2Id = :user2Id) or (c.participant1Id = :user2Id and c.participant2Id = :user1Id)")
    Optional<Conversation> findExistingConversation(@Param("user1Id") UUID user1Id, @Param("user2Id") UUID user2Id);

    //gets the user's inbox, sorted by the most recent messages
    @Query("select c from Conversation c where c.participant1Id = :userId or c.participant2Id = :userId order by c.lastMessageAt desc")
    List<Conversation> findAllByUserId(@Param("userId") UUID userId);
}
