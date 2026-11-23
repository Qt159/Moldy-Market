package com.moldy.moldymarket.feedback.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "feedbacks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_feedback_order_buyer",
                        columnNames = {"order_id", "buyer_id"}
                )
        }
)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    UUID orderId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    UUID buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    UUID sellerId;

    @Column(nullable = false)
    Integer rating;

    @Column(columnDefinition = "TEXT")
    String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Builder
    private Feedback(UUID orderId, UUID buyerId, UUID sellerId, Integer rating, String comment){
        this.orderId = orderId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.rating = rating;
        this.comment = comment;
    }
    @PrePersist
    void onCreate(){
        this.createdAt = Instant.now();
    }
}
