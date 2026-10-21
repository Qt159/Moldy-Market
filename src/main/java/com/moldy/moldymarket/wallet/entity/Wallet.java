package com.moldy.moldymarket.wallet.entity;

import com.moldy.moldymarket.user.entity.User;
import com.moldy.moldymarket.wallet.enums.WalletStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 2)
    BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "held_balance", nullable = false, precision = 19, scale = 2)
    BigDecimal heldBalance = BigDecimal.ZERO;

    @Column(name = "pending_withdraw_balance", nullable = false, precision = 19, scale = 2)
    BigDecimal pendingWithdrawBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    WalletStatus status = WalletStatus.ACTIVE;

    @Version
    Long version;

    @Column(name = "created_at", updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    Instant updatedAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
