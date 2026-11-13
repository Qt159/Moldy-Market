
package com.moldy.moldymarket.appraiser.repository;

import com.moldy.moldymarket.appraiser.entity.Appraiser;
import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppraiserRepository extends JpaRepository<Appraiser, UUID> {

    Optional<Appraiser> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    Page<Appraiser> findByStatus(AppraiserStatus status, Pageable pageable);

    @Query("""
        SELECT a
        FROM Appraiser a
        WHERE a.status = :status
        ORDER BY a.averageRating DESC NULLS LAST
        """)
    Page<Appraiser> findByStatusOrderByRating(
            @Param("status") AppraiserStatus status,
            Pageable pageable
    );
    Page<Appraiser> findByStatusAndIdIn(
        AppraiserStatus status,
        List<UUID> appraiserIds,
        Pageable pageable);
}
