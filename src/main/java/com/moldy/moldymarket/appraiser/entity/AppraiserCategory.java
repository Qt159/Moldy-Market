package com.moldy.moldymarket.appraiser.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

//chuyên môn
@Entity
@Table(name = "appraiser_categories")
@IdClass(AppraiserCategoryId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppraiserCategory {

    @Id
    @Column(name = "appraiser_id", nullable = false)
    private UUID appraiserId;

    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "verified", nullable = false)
    private boolean verified = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public AppraiserCategory(UUID appraiserId, UUID categoryId) {
        this.appraiserId = Objects.requireNonNull(appraiserId);
        this.categoryId = Objects.requireNonNull(categoryId);
    }

    public void verify() {
        this.verified = true;
    }

    public void unverify() {
        this.verified = false;
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
