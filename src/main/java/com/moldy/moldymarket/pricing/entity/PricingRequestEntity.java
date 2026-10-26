package com.moldy.moldymarket.pricing.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


//Pricing Request:Yêu cầu định giá (Auto hoặc Appraiser).
@Entity
@Table(
    name = "pricing_requests",
    indexes = {
        @Index(name = "idx_pricing_requests_seller", columnList = "seller_id"),
        @Index(name = "idx_pricing_requests_appraiser", columnList = "appraiser_id"),
        @Index(name = "idx_pricing_requests_status", columnList = "status"),
        @Index(name = "idx_pricing_requests_mode", columnList = "pricing_mode"),
        @Index(name = "idx_pricing_requests_created_at", columnList = "created_at DESC")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricingRequestEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PricingMode pricingMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PricingStatus status;

    // Linking
    @Column(nullable = true)  // todo update khi có listing
    UUID listingId;

    @Column(nullable = false)
    UUID sellerId;

    // Auto Pricing Input
    @Column(nullable = true)
    UUID categoryId;

    @Column(nullable = true)
    UUID modelId;

    @Column(nullable = true, length = 20)
    String origin;  // OFFICIAL_VN

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    WarrantyStatus warrantyStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    ConditionLevel conditionLevel;

    @Column(nullable = true)
    Integer usageMonths;

    // Auto Pricing Output
    @Column(nullable = true)
    BigDecimal suggestedPrice;

    @Column(nullable = true)
    BigDecimal referencePrice;

    @Column(nullable = true, precision = 5, scale = 4)
    BigDecimal deprecationFactor;

    @Column(nullable = true, precision = 5, scale = 4)
    BigDecimal conditionFactor;

    @Column(nullable = true, precision = 5, scale = 4)
    BigDecimal warrantyFactor;

    @Column(nullable = true, length = 200)
    String referenceModelName;

    @Column(nullable = true)
    Instant referencePriceUpdatedAt;

    // Appraiser Pricing
    @Column(nullable = true)
    UUID appraiserId;

    @Column(nullable = true, columnDefinition = "TEXT")
    String appraiserNote;

    @Column(nullable = true, precision = 5, scale = 2)
    BigDecimal commissionRate;

    @Column(nullable = false)
    Short rejectCount = 0;

    // Disclaimer
    @Column(nullable = true, columnDefinition = "TEXT")
    String disclaimer;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.rejectCount == null) {
            this.rejectCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public PricingRequestEntity(UUID sellerId, UUID categoryId, UUID modelId, String origin,
                               WarrantyStatus warrantyStatus, ConditionLevel conditionLevel,
                               Integer usageMonths) {
        this.pricingMode = PricingMode.AUTO;
        this.status = PricingStatus.SUGGESTED;
        this.sellerId = sellerId;
        this.categoryId = categoryId;
        this.modelId = modelId;
        this.origin = origin;
        this.warrantyStatus = warrantyStatus;
        this.conditionLevel = conditionLevel;
        this.usageMonths = usageMonths;
        this.rejectCount = 0;
    }
    public void setAutoPricingResult(BigDecimal suggestedPrice, BigDecimal referencePrice,
                                     BigDecimal deprecationFactor, BigDecimal conditionFactor,
                                     BigDecimal warrantyFactor, String referenceModelName,
                                     Instant referencePriceUpdatedAt, String disclaimer) {
        this.suggestedPrice = suggestedPrice;
        this.referencePrice = referencePrice;
        this.deprecationFactor = deprecationFactor;
        this.conditionFactor = conditionFactor;
        this.warrantyFactor = warrantyFactor;
        this.referenceModelName = referenceModelName;
        this.referencePriceUpdatedAt = referencePriceUpdatedAt;
        this.disclaimer = disclaimer;
    }

    public void setManualRequired(String reason) {
        this.status = PricingStatus.MANUAL_REQUIRED;
        this.disclaimer = reason;
    }
}
