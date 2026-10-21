package com.moldy.moldymarket.pricing.mapper;

import com.moldy.moldymarket.pricing.dto.ReferencePriceRequest;
import com.moldy.moldymarket.pricing.dto.ReferencePriceResponse;
import com.moldy.moldymarket.pricing.entity.ReferencePriceEntity;
import org.springframework.stereotype.Component;

<<<<<<< HEAD
/**
 * Mapper for ReferencePriceEntity <-> DTO conversions.
 */
@Component
public class ReferencePriceMapper {

    /**
     * Map ReferencePriceEntity to ReferencePriceResponse DTO.
     */
=======

@Component
public class ReferencePriceMapper {

>>>>>>> origin/feature/voucher
    public ReferencePriceResponse toResponse(ReferencePriceEntity entity) {
        if (entity == null) {
            return null;
        }

        return new ReferencePriceResponse(
            entity.getId(),
            entity.getCategoryId(),
            entity.getBrand(),
            entity.getModelName(),
            entity.getReferencePrice().longValue(),
            entity.getCurrency(),
            entity.getUpdatedAt(),
            entity.getUpdatedBy()
        );
    }

<<<<<<< HEAD
    /**
     * Map ReferencePriceRequest DTO to ReferencePriceEntity for creation.
     */
=======
    
>>>>>>> origin/feature/voucher
    public ReferencePriceEntity toEntity(ReferencePriceRequest request) {
        if (request == null) {
            return null;
        }

        ReferencePriceEntity entity = new ReferencePriceEntity();
        entity.setCategoryId(request.categoryId());
        entity.setBrand(request.brand());
        entity.setModelName(request.modelName());
        entity.setReferencePrice(request.referencePrice());
        entity.setCurrency("VND");
        
        return entity;
    }

<<<<<<< HEAD
    /**
     * Update ReferencePriceEntity from ReferencePriceRequest.
     */
=======
   
>>>>>>> origin/feature/voucher
    public void updateEntity(ReferencePriceRequest request, ReferencePriceEntity entity) {
        if (request == null || entity == null) {
            return;
        }

        entity.setBrand(request.brand());
        entity.setModelName(request.modelName());
        entity.setReferencePrice(request.referencePrice());
    }
}
