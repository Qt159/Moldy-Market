
package com.moldy.moldymarket.pricing.reference.mapper;

import com.moldy.moldymarket.pricing.reference.dto.ReferencePriceRequest;
import com.moldy.moldymarket.pricing.reference.dto.ReferencePriceResponse;
import com.moldy.moldymarket.pricing.entity.ReferencePriceEntity;
import org.springframework.stereotype.Component;

// Mapper for ReferencePriceEntity.
@Component
public class ReferencePriceMapper {

    public ReferencePriceResponse toResponse(ReferencePriceEntity entity) {
        if (entity == null) {
            return null;}

        return new ReferencePriceResponse(
                entity.getId(),
                entity.getCategoryId(),
                entity.getBrand(),
                entity.getModelName(),
                entity.getReferencePrice(),
                entity.getCurrency(),
                entity.getUpdatedAt(),
                entity.getUpdatedBy(),
                entity.getCreatedAt());
    }

    public ReferencePriceEntity toEntity(ReferencePriceRequest request) {
        if (request == null) {
            return null;}

        return new ReferencePriceEntity(
                request.categoryId(),
                request.brand(),
                request.modelName(),
                request.referencePrice(),
                null);
    }

    public void updateEntity(
            ReferencePriceRequest request,
            ReferencePriceEntity entity) {
        if (request == null || entity == null) {
            return;
        }
        entity.updatePrice(
                request.referencePrice(),
                entity.getUpdatedBy());
    }
}
