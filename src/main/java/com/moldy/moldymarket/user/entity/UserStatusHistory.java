package com.moldy.moldymarket.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(indexes = @Index(name = "idx_user_status_history_user_created", columnList = "user_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UserStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    UserStatus toStatus;

    String reason;

    //actor is admin
    @Column(nullable = false)
    UUID actorId;

    @Column(nullable = false, updatable = false)
    Instant createdAt;

    @Builder
    private UserStatusHistory(UUID userId, UserStatus fromStatus, UserStatus toStatus,
                              String reason, UUID actorId, Instant createdAt) {
        this.userId = userId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.actorId = actorId;
        this.createdAt = createdAt;
    }
}
