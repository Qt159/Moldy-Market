package com.moldy.moldymarket.commission.repository;

import com.moldy.moldymarket.commission.entity.AppraiserCommission;
import com.moldy.moldymarket.commission.enums.CommissionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppraiserCommissionRepository extends JpaRepository<AppraiserCommission, UUID> {
    Optional<AppraiserCommission> findByWorkflowId(UUID workflowId);

    Page<AppraiserCommission> findByAppraiserId(UUID appraiserId, Pageable pageable);

    Page<AppraiserCommission> findByAppraiserIdAndStatus(UUID appraiserId, CommissionStatus status, Pageable pageable);

    @Query("SELECT COALESCE(SUM(c.netEarnings), 0) FROM AppraiserCommission c WHERE c.appraiserId = :appraiserId AND c.status = 'PAID'")
    BigDecimal getTotalEarningsByAppraiser(UUID appraiserId);

    int countByAppraiserIdAndStatus(UUID appraiserId, CommissionStatus status);
}
