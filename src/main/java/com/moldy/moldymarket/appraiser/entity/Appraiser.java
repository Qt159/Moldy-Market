package com.moldy.moldymarket.appraiser.entity;

import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import com.moldy.moldymarket.appraiser.enums.VerificationLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.math.RoundingMode;

@Entity
@Table(name = "appraiser_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appraiser {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppraiserStatus status = AppraiserStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_level", nullable = false)
    private VerificationLevel verificationLevel = VerificationLevel.UNVERIFIED;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;
    
    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;
    
    @Column(name = "total_completed_appraisals", nullable = false)
    private int totalCompletedAppraisals;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "rating_sum", nullable = false)
    private long ratingSum = 0;

    @Column(name = "review_count", nullable = false)
    private int reviewCount= 0;
   
    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;
   
    @Column(name = "suspension_until")
    private Instant suspensionUntil;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    public Appraiser(UUID userId) {
        if(userId == null){
            throw new IllegalArgumentException("User ID is required");
        }
        this.userId = userId;
    }

    public void updateProfile(String bio, String avatarUrl){
         if (bio != null) {
            this.bio = bio;}

        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl;}
    }


    public void approve() {
        if (this.status != AppraiserStatus.PENDING) {
            throw new IllegalStateException("Only PENDING appraisers can be approved");
        }
        this.status = AppraiserStatus.ACTIVE;
        this.verificationLevel = VerificationLevel.BASIC;
        this.rejectionReason = null;
    }

    public void reject(String reason) {
         if (this.status != AppraiserStatus.PENDING) {
            throw new IllegalStateException("Only PENDING appraisers can be rejected");
        }
        if(reason == null || reason.isBlank()){
            throw new IllegalArgumentException("Rejection reason is required");
        }
        this.status = AppraiserStatus.REJECTED;
        this.rejectionReason = reason;

    }

    
    public void suspend(String reason, Instant until) {
        if (this.status != AppraiserStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE appraisers can be suspended");}
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Suspension reason is required");}
        // tránh NPE
        if(until != null && !until.isAfter(Instant.now())){
            throw new IllegalArgumentException("Suspension end time must be in the future");
        }
        this.status = AppraiserStatus.SUSPENDED;
        this.suspensionReason = reason;
        this.suspensionUntil = until;
    }

    
    public void resume() {
        if (this.status != AppraiserStatus.SUSPENDED) {
            throw new IllegalStateException("Only SUSPENDED appraisers can be resumed");
        }
        this.status = AppraiserStatus.ACTIVE;
        this.suspensionReason = null;
        this.suspensionUntil = null;
    }

    
    public void deactivate() {
        if (this.status != AppraiserStatus.ACTIVE) {
             throw new IllegalStateException("Only ACTIVE appraisers can be deactivated");
        }
        this.status = AppraiserStatus.INACTIVE;
    }

    public void reactivate() {
        if (this.status != AppraiserStatus.INACTIVE) {
            throw new IllegalStateException("Only INACTIVE appraisers can be reactivated");
        }
        this.status = AppraiserStatus.ACTIVE;
    }
    
    public boolean isActive() {
        return this.status == AppraiserStatus.ACTIVE;
    }

    public void recordReview(Integer rating) {
        if(rating == null || rating <1 || rating >5){
            throw new IllegalArgumentException( "Rating must be between 1 and 5");
        }
        this.ratingSum += rating;
        this.reviewCount++;
        
        this.averageRating = BigDecimal.valueOf(this.ratingSum)
            .divide(BigDecimal.valueOf(this.reviewCount),
            2, RoundingMode.HALF_UP);
    }
    
    public void recordCompletedAppraisal() {
        this.totalCompletedAppraisals++;
    }

  

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
