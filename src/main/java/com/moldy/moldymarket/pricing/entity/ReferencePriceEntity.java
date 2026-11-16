package com.moldy.moldymarket.pricing.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reference_prices")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferencePriceEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private UUID categoryId;
    
    @Column(nullable = false, length = 100)
    private String brand;
    
    @Column(nullable = false, length = 200)
    private String modelName;
    
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal referencePrice;
    
    @Column(nullable = false, length = 3)
    private String currency;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "updated_by")
    private UUID updatedBy;
    
    @Version
    private Long version;

    public ReferencePriceEntity(UUID categoryId, String brand, String modelName, BigDecimal referencePrice, UUID updatedBy) {
        this.categoryId = categoryId;
        this.brand = brand;
        this.modelName = modelName;
        this.referencePrice = referencePrice;
        this.currency = "VND"; // Default currency
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public void updatePrice(BigDecimal referencePrice, UUID updatedBy) {
        this.referencePrice = referencePrice;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
