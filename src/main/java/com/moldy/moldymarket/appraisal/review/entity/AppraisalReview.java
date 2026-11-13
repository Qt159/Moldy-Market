package com.moldy.moldymarket.appraisal.review.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "appraisal_reviews",
        uniqueConstraints = {@UniqueConstraint(
                        name = "uk_appraisal_review_assignment",
                        columnNames = "assignment_id")}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraisalReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(name = "appraiser_id", nullable = false)
    private UUID appraiserId;

    @Column(nullable = false)
    private Integer rating;

    @Column(name = "accuracy_score", nullable = false)
    private Integer accuracyScore;

    @Column(name = "professionalism_score", nullable = false)
    private Integer professionalismScore;

    @Column(name = "communication_score", nullable = false)
    private Integer communicationScore;

    @Column(name = "timeliness_score", nullable = false)
    private Integer timelinessScore;

    @Column(length = 2000)
    private String comment;

    @Column(nullable = false)
    private Boolean recommend;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    public AppraisalReview(
            UUID assignmentId,
            UUID sellerId,
            UUID appraiserId,
            Integer rating,
            Integer accuracyScore,
            Integer professionalismScore,
            Integer communicationScore,
            Integer timelinessScore,
            String comment,
            Boolean recommend
    ) {
        this.assignmentId = assignmentId;
        this.sellerId = sellerId;
        this.appraiserId = appraiserId;
        this.rating = rating;
        this.accuracyScore = accuracyScore;
        this.professionalismScore = professionalismScore;
        this.communicationScore = communicationScore;
        this.timelinessScore = timelinessScore;
        this.comment = comment;
        this.recommend = recommend;
        this.createdAt = Instant.now();
    }
}