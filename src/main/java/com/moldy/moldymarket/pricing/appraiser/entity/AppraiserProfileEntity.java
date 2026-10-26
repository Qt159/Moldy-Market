package com.moldy.moldymarket.pricing.appraiser.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.UUID;


// Hồ sơ người định giá: Lưu trữ trạng thái, điểm uy tín, số lần hoàn thành.
@Entity
@Table(
    name = "appraiser_profiles",
    indexes = {
        @Index(name = "idx_appraiser_profiles_status", columnList = "status"),
        @Index(name = "idx_appraiser_profiles_user_id", columnList = "user_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_appraiser_user", columnNames = "user_id")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AppraiserProfileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, unique = true)
    UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AppraiserStatus status = AppraiserStatus.PENDING;

    @Column(nullable = false)
    Integer totalCompleted = 0;

    @Column(nullable = false)
    Integer accurateCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.totalCompleted == null) {
            this.totalCompleted = 0;
        }
        if (this.accurateCount == null) {
            this.accurateCount = 0;
        }
        if (this.status == null) {
            this.status = AppraiserStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public AppraiserProfileEntity(UUID userId) {
        this.userId = userId;
        this.status = AppraiserStatus.PENDING;
        this.totalCompleted = 0;
        this.accurateCount = 0;
    }

    public Double getAccuracyPercentage() {
        if (totalCompleted == 0) {
            return null;  // Not enough data
        }
        return (accurateCount.doubleValue() / totalCompleted.doubleValue()) * 100;
    }

    public boolean isApproved() {
        return status == AppraiserStatus.ACTIVE;
    }

    public void approve() {
        this.status = AppraiserStatus.ACTIVE;
    }

    public void suspend() {
        this.status = AppraiserStatus.SUSPENDED;
    }

    public void incrementCompletedCount(boolean isAccurate) {
        this.totalCompleted++;
        if (isAccurate) {
            this.accurateCount++;
        }
        this.updatedAt = Instant.now();
    }
}
