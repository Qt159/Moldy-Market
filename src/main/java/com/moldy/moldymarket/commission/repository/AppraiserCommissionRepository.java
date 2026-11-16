package com.moldy.moldymarket.commission.repository;

import com.moldy.moldymarket.commission.entity.AppraiserCommission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppraiserCommissionRepository
    extends JpaRepository<AppraiserCommission, UUID> {

    Optional<AppraiserCommission> findByAssignmentId(UUID assignmentId);

    Optional<AppraiserCommission> findByIdAndAppraiserId(UUID id, UUID appraiserId);

    List<AppraiserCommission> findAllByAppraiserIdOrderByCalculatedAtDesc(UUID appraiserId);

    boolean existsByAssignmentId(UUID assignmentId);
}