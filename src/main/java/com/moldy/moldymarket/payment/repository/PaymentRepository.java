package com.moldy.moldymarket.payment.repository;

import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.entity.PaymentTransaction;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPaymentCode(String paymentCode);
    List<Payment> findAllByPayerIdOrderByCreatedAtDesc(UUID payerId);
    Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);



    @Query("select p from Payment p where p.orderId = :orderId and p.status = :paymentStatus")
    Optional<Payment> findByOrderIdAndStatus(@Param("orderId") UUID orderId, @Param("paymentStatus") PaymentStatus paymentStatus);

    @Query("select p.status from Payment p where p.id = :id")
    PaymentStatus findStatusById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true)
    @Query("""
        update Payment p
            set p.status = com.moldy.moldymarket.payment.enums.PaymentStatus.SUCCESSFUL,
                p.paidAt = :now, p.updatedAt = :now,
                p.paymentTransaction = :paymentTransaction,
                p.version = p.version + 1
        where p.id = :id 
            and p.status = com.moldy.moldymarket.payment.enums.PaymentStatus.PENDING
    """)
    int markPaid(@Param("id") UUID id, @Param("paymentTransaction") PaymentTransaction paymentTransaction, @Param("now") Instant now);
}
