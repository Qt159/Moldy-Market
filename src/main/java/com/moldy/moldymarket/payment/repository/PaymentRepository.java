package com.moldy.moldymarket.payment.repository;

import com.moldy.moldymarket.payment.entity.Payment;
import com.moldy.moldymarket.payment.enums.PaymentStatus;
import com.moldy.moldymarket.payment.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByPaymentCode(String paymentCode);
    List<Payment> findAllByPayerIdOrderByCreatedAtDesc(UUID payerId);
    //Optional<Payment> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    Optional<Payment> findByCheckoutGroupId(UUID checkoutGroupId);



    @Query("select p from Payment p where p.checkoutGroupId = :checkoutGroupId and p.status = :paymentStatus")
    Optional<Payment> findByCheckoutGroupIdAndStatus(@Param("checkoutGroupId") UUID checkoutGroupId, @Param("paymentStatus") PaymentStatus paymentStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    Slice<Payment> findByMethodAndStatusOrderByCreatedAtAsc(
            PaymentMethod method, PaymentStatus status, Pageable pageable);
}
