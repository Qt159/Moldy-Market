package com.moldy.moldymarket.dispute.repository;

import com.moldy.moldymarket.dispute.entity.DisputeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<DisputeRequest, UUID> {

    Optional<DisputeRequest> findByOrderId(UUID orderId);

    @Query("""
        select d from DisputeRequest d
        where d.buyerId = :buyerId
""")
    List<DisputeRequest> findAllDisputeByBuyerId(@Param("buyerId") UUID buyerId);


}