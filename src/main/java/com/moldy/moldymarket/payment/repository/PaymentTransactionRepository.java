package com.moldy.moldymarket.payment.repository;

import com.moldy.moldymarket.payment.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {
    boolean existsByProviderAndExternalId(String provider, String externalId);
}
