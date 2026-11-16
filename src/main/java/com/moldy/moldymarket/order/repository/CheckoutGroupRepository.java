package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CheckoutGroupRepository extends JpaRepository<CheckoutGroup, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CheckoutGroup c where c.id = :id")
    Optional<CheckoutGroup> findByIdForUpdate(@Param("id") UUID id);
}
