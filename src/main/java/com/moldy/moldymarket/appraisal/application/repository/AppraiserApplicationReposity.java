package com.moldy.moldymarket.appraisal.application.repository;

import com.moldy.moldymarket.appraisal.application.entity.AppraiserApplication;
import com.moldy.moldymarket.appraisal.application.enums.AppraiserApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppraiserApplicationRepository
        extends JpaRepository<AppraiserApplication, UUID> {

    boolean existsByAppraisalRequestIdAndAppraiserId(UUID appraisalRequestId, UUID appraiserId);

    Page<AppraiserApplication> findAllByAppraisalRequestId( UUID appraisalRequestId, Pageable pageable);

    Page<AppraiserApplication> findAllByAppraiserId( UUID appraiserId, Pageable pageable);

    List<AppraiserApplication> findAllByAppraisalRequestIdAndStatus(
                    UUID appraisalRequestId, AppraiserApplicationStatus status);

    Optional<AppraiserApplication> findByIdAndAppraiserId(UUID id, UUID appraiserId);

    long countByAppraisalRequestIdAndStatus(
            UUID appraisalRequestId, AppraiserApplicationStatus status);
}
