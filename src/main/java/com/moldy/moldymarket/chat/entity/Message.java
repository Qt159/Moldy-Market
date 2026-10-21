package com.moldy.moldymarket.chat.entity;

import com.moldy.moldymarket.chat.enums.ConversationStatus;
import com.moldy.moldymarket.chat.enums.MessageType;
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
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    Conversation conversation;

    @Column(name = "sender_id", nullable = false)
    UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    MessageType type;

    @Column(columnDefinition = "TEXT")
    String content;

    @Column(name = "attached_product_id")
    UUID attachedProductId;

    @Column(name = "attachment_url", length = 500)
    String attachmentUrl;

    @Column(name = "sent_at", updatable = false)
    Instant sentAt = Instant.now();
}
