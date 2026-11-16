package com.moldy.moldymarket.wallet.entity;

import com.moldy.moldymarket.wallet.enums.EscrowStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Table(name = "escrow_holds",
        uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Entity
public class EscrowHold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "order_id", nullable = false)
    String orderId;

    @Column(name = "buyer_wallet_id", nullable = false)
    Long buyerWalletId;

    @Column(name = "seller_wallet_id", nullable = false)
    Long sellerWalletId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    BigDecimal amount;

    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "captured_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "released_amount", nullable = false, precision = 19, scale = 2)
    BigDecimal releasedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    EscrowStatus status = EscrowStatus.HELD;

    @Version
    Long version;

    @Column(name = "created_at", updatable = false)
    Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    Instant resolvedAt;
}
