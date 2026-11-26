package com.moldy.moldymarket.offer.repository;

import com.moldy.moldymarket.offer.entity.Offer;
import com.moldy.moldymarket.offer.enums.OfferSide;
import com.moldy.moldymarket.offer.enums.OfferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Offer o where o.id = :id")
    Optional<Offer> findByIdForUpdate(@Param("id") UUID id);

    List<Offer> findAllByStatusAndExpiresAtLessThan(
            OfferStatus status,
            Instant now
    );
    long countByBuyerIdAndProductIdAndSide(UUID buyerId, UUID productId, OfferSide side);

    boolean existsByBuyerIdAndProductIdAndStatusIn(UUID buyerId, UUID productId, Collection<OfferStatus> statuses);

    Page<Offer> findAllByNegotiationId(UUID negotiationId, Pageable pageable);
    Page<Offer> findAllByBuyerIdAndStatusNot(UUID buyerId, OfferStatus status, Pageable pageable);
    Page<Offer> findAllBySellerIdAndStatusNot(UUID sellerId, OfferStatus status, Pageable pageable);
    Page<Offer> findAllByStoreIdAndStatusNot(UUID storeId, OfferStatus status, Pageable pageable);

    Optional<Offer> findTopByNegotiationIdOrderByCreatedAtDescIdDesc(UUID negotiationId);
}
