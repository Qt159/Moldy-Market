package com.moldy.moldymarket.appraisal.report.entity;

import com.moldy.moldymarket.appraisal.report.enums.AppraisalReportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "appraisal_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraisalReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "appraiser_id", nullable = false)
    private UUID appraiserId;

    @Column(name = "revision_number", nullable = false)
    private Integer revisionNumber;

    @Column(name = "description", nullable = false, length = 5000)
    private String description;

    @Column(name = "condition_assessment", nullable = false, length = 5000)
    private String conditionAssessment;

    @Column(name = "assessed_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal assessedValue;

    @ElementCollection
    @CollectionTable(
            name = "appraisal_report_images",
            joinColumns = @JoinColumn(name = "report_id")
    )
    @Column(name = "image_url", nullable = false, length = 1000)
    private List<String> imageUrls = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "appraisal_report_documents",
            joinColumns = @JoinColumn(name = "report_id")
    )
    @Column(name = "document_url", nullable = false, length = 1000)
    private List<String> documentUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppraisalReportStatus status;

    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    private Long version;

    public AppraisalReport(
            UUID assignmentId,
            UUID appraiserId,
            Integer revisionNumber,
            String description,
            String conditionAssessment,
            BigDecimal assessedValue,
            List<String> imageUrls,
            List<String> documentUrls
    ) {
        this.assignmentId = assignmentId;
        this.appraiserId = appraiserId;
        this.revisionNumber = revisionNumber;
        this.description = description;
        this.conditionAssessment = conditionAssessment;
        this.assessedValue = assessedValue;
        this.imageUrls = imageUrls == null
                ? new ArrayList<>()
                : new ArrayList<>(imageUrls);
        this.documentUrls = documentUrls == null
                ? new ArrayList<>()
                : new ArrayList<>(documentUrls);
        this.status = AppraisalReportStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    public void accept() {
        validateSubmitted();

        this.status = AppraisalReportStatus.ACCEPTED;
        this.reviewedAt = Instant.now();
    }

    public void reject(String reason) {
        validateSubmitted();

        this.status = AppraisalReportStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedAt = Instant.now();
    }

    private void validateSubmitted() {
        if (this.status != AppraisalReportStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only submitted reports can be reviewed");
        }
    }
}