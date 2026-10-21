package com.moldy.moldymarket.chat.entity;

import com.moldy.moldymarket.chat.enums.ConversationStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "participant1_id", nullable = false)
    UUID participant1Id;

    @Column(name  = "participant2_id", nullable = false)
    UUID participant2Id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    ConversationStatus status = ConversationStatus.ACTIVE;

    @Column(name = "last_message_at")
    Instant lastMessageAt = Instant.now();

    @Column(name = "last_read_at_participant1")
    Instant lastReadAtParticipant1;

    @Column(name = "last_read_at_participant2")
    Instant lastReadAtParticipant2;

    @Column(name = "created_at", updatable = false)
    Instant createdAt = Instant.now();
}
