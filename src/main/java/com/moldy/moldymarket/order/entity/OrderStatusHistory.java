package com.moldy.moldymarket.order.entity;

import com.moldy.moldymarket.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;

@Setter
@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_status_history")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", updatable = false)
    OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    OrderStatus toStatus;

    @Column(name = "actor_id", updatable = false)
    UUID actorId;

    @Column(name = "note", updatable = false)
    String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt = Instant.now();


}
