package com.moldy.moldymarket.appraisal.report.repository;

import com.moldy.moldymarket.appraisal.report.entity.AppraisalReport;
import com.moldy.moldymarket.appraisal.report.enums.AppraisalReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppraisalReportRepository
        extends JpaRepository<AppraisalReport, UUID> {

    List<AppraisalReport> findAllByAssignmentIdOrderByRevisionNumberDesc(
            UUID assignmentId);

    Optional<AppraisalReport> findByIdAndAssignmentId(
            UUID reportId, UUID assignmentId);

    Optional<AppraisalReport> findFirstByAssignmentIdOrderByRevisionNumberDesc(
            UUID assignmentId);

    boolean existsByAssignmentIdAndStatus(
            UUID assignmentId,
            AppraisalReportStatus status);

    boolean existsByAssignmentIdAndRevisionNumber(
            UUID assignmentId,
            Integer revisionNumber);
}