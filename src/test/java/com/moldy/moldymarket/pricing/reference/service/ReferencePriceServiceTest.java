package com.moldy.moldymarket.pricing.reference.service;

import com.moldy.moldymarket.pricing.entity.ReferencePriceEntity;
import com.moldy.moldymarket.pricing.reference.dto.ReferencePriceRequest;
import com.moldy.moldymarket.pricing.reference.dto.ReferencePriceResponse;
import com.moldy.moldymarket.pricing.reference.mapper.ReferencePriceMapper;
import com.moldy.moldymarket.pricing.reference.repository.ReferencePriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReferencePriceServiceTest {

    @Mock
    private ReferencePriceRepository referencePriceRepository;

    @Mock
    private ReferencePriceMapper referencePriceMapper;

    @InjectMocks
    private ReferencePriceService referencePriceService;

    private UUID adminId;
    private UUID categoryId;
    private UUID referencePriceId;
    private ReferencePriceEntity sampleEntity;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        referencePriceId = UUID.randomUUID();

        sampleEntity = new ReferencePriceEntity(
                categoryId, "Sony", "WH-1000XM5", BigDecimal.valueOf(8000000), adminId
        );
        ReflectionTestUtils.setField(sampleEntity, "id", referencePriceId);
    }

    @Test
    @DisplayName("should create reference price when brand and model do not exist")
    void should_createReferencePrice_when_valid() {
        // Arrange
        ReferencePriceRequest request = new ReferencePriceRequest(
                categoryId, "Sony", "WH-1000XM5", BigDecimal.valueOf(8000000)
        );

        when(referencePriceRepository.existsByBrandAndModelName("Sony", "WH-1000XM5")).thenReturn(false);
        when(referencePriceRepository.save(any(ReferencePriceEntity.class))).thenReturn(sampleEntity);

        ReferencePriceResponse expectedResponse = mock(ReferencePriceResponse.class);
        when(referencePriceMapper.toResponse(sampleEntity)).thenReturn(expectedResponse);

        // Act
        ReferencePriceResponse response = referencePriceService.createReferencePrice(request, adminId);

        // Assert
        assertThat(response).isNotNull();
        verify(referencePriceRepository).save(any(ReferencePriceEntity.class));
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when brand and model already exist")
    void should_throwException_when_brandAndModelAlreadyExist() {
        // Arrange
        ReferencePriceRequest request = new ReferencePriceRequest(
                categoryId, "Sony", "WH-1000XM5", BigDecimal.valueOf(8000000)
        );
        when(referencePriceRepository.existsByBrandAndModelName("Sony", "WH-1000XM5")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> referencePriceService.createReferencePrice(request, adminId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reference price already exists");

        verify(referencePriceRepository, never()).save(any());
    }

    @Test
    @DisplayName("should update reference price successfully when entity exists")
    void should_updateReferencePrice_when_valid() {
        // Arrange
        ReferencePriceRequest request = new ReferencePriceRequest(
                categoryId, "Sony", "WH-1000XM5", BigDecimal.valueOf(7500000)
        );

        when(referencePriceRepository.findById(referencePriceId)).thenReturn(Optional.of(sampleEntity));
        when(referencePriceRepository.save(sampleEntity)).thenReturn(sampleEntity);
        when(referencePriceMapper.toResponse(sampleEntity)).thenReturn(mock(ReferencePriceResponse.class));

        // Act
        ReferencePriceResponse response = referencePriceService.updateReferencePrice(referencePriceId, request, adminId);

        // Assert
        assertThat(response).isNotNull();
        assertThat(sampleEntity.getReferencePrice()).isEqualByComparingTo(BigDecimal.valueOf(7500000));
        verify(referencePriceRepository).save(sampleEntity);
    }

    @Test
    @DisplayName("should throw IllegalArgumentException when reference price not found by id")
    void should_throwException_when_idNotFound() {
        // Arrange
        UUID unknownId = UUID.randomUUID();
        when(referencePriceRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> referencePriceService.getReferencePrice(unknownId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Reference price not found");
    }

    @Test
    @DisplayName("should get reference price by brand and model name")
    void should_getReferencePrice_when_brandAndModelExist() {
        // Arrange
        when(referencePriceRepository.findByBrandAndModelName("Sony", "WH-1000XM5"))
                .thenReturn(Optional.of(sampleEntity));
        when(referencePriceMapper.toResponse(sampleEntity)).thenReturn(mock(ReferencePriceResponse.class));

        // Act
        ReferencePriceResponse response = referencePriceService.getReferencePrice("Sony", "WH-1000XM5");

        // Assert
        assertThat(response).isNotNull();
    }
}
