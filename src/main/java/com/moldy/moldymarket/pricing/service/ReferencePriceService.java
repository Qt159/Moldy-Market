package com.moldy.moldymarket.pricing.service;

import com.moldy.moldymarket.pricing.dto.ReferencePriceRequest;
import com.moldy.moldymarket.pricing.dto.ReferencePriceResponse;
import com.moldy.moldymarket.pricing.entity.ReferencePriceEntity;
import com.moldy.moldymarket.pricing.repository.ReferencePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Transactional
public class ReferencePriceService {
    private final ReferencePriceRepository referencePriceRepository;

    
    public ReferencePriceResponse createReferencePrice(ReferencePriceRequest request, UUID adminId) {
       
        if (referencePriceRepository.existsByBrandAndModelName(request.brand(), request.modelName())) {
            throw new IllegalArgumentException(
                String.format("Reference price already exists for brand=%s, model=%s", 
                    request.brand(), request.modelName())
            );
        }

        ReferencePriceEntity entity = new ReferencePriceEntity(
            request.categoryId(),
            request.brand(),
            request.modelName(),
            request.referencePrice(),
            adminId
        );

        ReferencePriceEntity saved = referencePriceRepository.save(entity);
        return mapToResponse(saved);
    }

    
    public ReferencePriceResponse updateReferencePrice(UUID id, ReferencePriceRequest request, UUID adminId) {
        ReferencePriceEntity entity = referencePriceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reference price not found: " + id));

    
        if (!entity.getBrand().equals(request.brand()) || !entity.getModelName().equals(request.modelName())) {
            if (referencePriceRepository.existsByBrandAndModelName(request.brand(), request.modelName())) {
                throw new IllegalArgumentException(
                    String.format("Reference price already exists for brand=%s, model=%s", 
                        request.brand(), request.modelName())
                );
            }
        }

        entity.updatePrice(request.referencePrice(), adminId);
        ReferencePriceEntity updated = referencePriceRepository.save(entity);
        return mapToResponse(updated);
    }

  
    @Transactional(readOnly = true)
    public ReferencePriceResponse getReferencePrice(UUID id) {
        ReferencePriceEntity entity = referencePriceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Reference price not found: " + id));
        return mapToResponse(entity);
    }

    
    @Transactional(readOnly = true)
    public ReferencePriceResponse getReferencePrice(String brand, String modelName) {
        ReferencePriceEntity entity = referencePriceRepository.findByBrandAndModelName(brand, modelName)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format("Reference price not found for brand=%s, model=%s", brand, modelName)
            ));
        return mapToResponse(entity);
    }

   
    @Transactional(readOnly = true)
    public Page<ReferencePriceResponse> getAllReferencePrices(Pageable pageable) {
        Page<ReferencePriceEntity> entities = referencePriceRepository.findAll(pageable);
        List<ReferencePriceResponse> responses = entities.getContent().stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, entities.getTotalElements());
    }

  
    public void deleteReferencePrice(UUID id) {
        if (!referencePriceRepository.existsById(id)) {
            throw new IllegalArgumentException("Reference price not found: " + id);
        }
        referencePriceRepository.deleteById(id);
    }

    
    private ReferencePriceResponse mapToResponse(ReferencePriceEntity entity) {
        return new ReferencePriceResponse(
            entity.getId(),
            entity.getCategoryId(),
            entity.getBrand(),
            entity.getModelName(),
            entity.getReferencePrice(),
            entity.getCurrency(),
            entity.getUpdatedAt(),
            entity.getUpdatedBy(),
            entity.getCreatedAt()
        );
    }
}
