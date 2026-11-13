package com.moldy.moldymarket.appraisal.request.repository;

import com.moldy.moldymarket.appraisal.enums.AppraisalRequestStatus;
import com.moldy.moldymarket.appraisal.request.entity.AppraisalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppraisalRequestRepository
        extends JpaRepository<AppraisalRequest, UUID> {

    Page<AppraisalRequest> findAllBySellerId(UUID sellerId, Pageable pageable);
    Page<AppraisalRequest> findAllByStatus(AppraisalRequestStatus status,Pageable pageable);
    boolean existsByIdAndSellerId(UUID id, UUID sellerId);
}
