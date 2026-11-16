package com.moldy.moldymarket.order.repository;

import com.moldy.moldymarket.order.entity.CheckoutGroup;
import com.moldy.moldymarket.order.enums.CheckoutGroupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CheckoutGroupRepository extends JpaRepository<CheckoutGroup, UUID> {

    @Query("""
            select cg from CheckoutGroup  cg
            where cg.expiredAt <= :now
                  and cg.status in :statuses
            """)
    List<CheckoutGroup> findExpiredCheckouts(Instant now, Collection<CheckoutGroupStatus> statuses);
}
