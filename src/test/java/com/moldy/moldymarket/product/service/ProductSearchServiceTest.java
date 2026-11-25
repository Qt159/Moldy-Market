package com.moldy.moldymarket.product.service;

import com.moldy.moldymarket.category.repository.CategoryRepository;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.storage.service.FileStorageService;
import com.moldy.moldymarket.product.dto.ProductResponse;
import com.moldy.moldymarket.product.dto.ProductSearchRequest;
import com.moldy.moldymarket.product.dto.ProductSearchResponse;
import com.moldy.moldymarket.product.entity.PricingStrategy;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.mapper.ProductMapper;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.product.search.ProductSearchCriteria;
import com.moldy.moldymarket.product.search.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTest {

    @Mock
    private ProductSearchRepository searchRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ProductSearchService productSearchService;

    private UUID productId;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        sampleProduct = new Product(
                UUID.randomUUID(), null, UUID.randomUUID(),
                "MacBook Air M2", "Apple Silicon",
                BigDecimal.valueOf(20000000), ProductCondition.LIKE_NEW,
                PricingStrategy.SELLER_SET, List.of(), null
        );
        ReflectionTestUtils.setField(sampleProduct, "id", productId);
    }

    @Test
    @DisplayName("should perform search and return ProductSearchResponse with preserved order")
    void should_search_when_validRequest() {
        // Arrange
        ProductSearchRequest request = new ProductSearchRequest(
                "macbook", null, null, null, null, null, null, null, null, false
        );
        Pageable pageable = PageRequest.of(0, 10);

        when(searchRepository.findPageIds(any(ProductSearchCriteria.class), eq(10), eq(0L)))
                .thenReturn(List.of(productId));
        when(productRepository.findAllById(List.of(productId)))
                .thenReturn(List.of(sampleProduct));

        ProductResponse responseDto = mock(ProductResponse.class);
        when(productMapper.toPublicResponse(sampleProduct)).thenReturn(responseDto);

        // Act
        ProductSearchResponse response = productSearchService.search(request, pageable);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("should throw INVALID_REQUEST when minPrice > maxPrice")
    void should_throwInvalidRequest_when_minPriceGreaterThanMaxPrice() {
        // Arrange
        ProductSearchRequest request = new ProductSearchRequest(
                null, null, null, BigDecimal.valueOf(5000000), BigDecimal.valueOf(1000000), null, null, null, null, false
        );
        Pageable pageable = PageRequest.of(0, 10);

        // Act & Assert
        assertThatThrownBy(() -> productSearchService.search(request, pageable))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REQUEST);

        verify(searchRepository, never()).findPageIds(any(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("should return empty suggestions when query length < 3")
    void should_returnEmptySuggestions_when_queryTooShort() {
        // Act
        List<String> suggestions = productSearchService.suggest("ab");

        // Assert
        assertThat(suggestions).isEmpty();
        verify(searchRepository, never()).suggestTitles(anyString(), anyInt());
    }

    @Test
    @DisplayName("should return suggestions when query length >= 3")
    void should_returnSuggestions_when_queryValid() {
        // Arrange
        when(searchRepository.suggestTitles(eq("iphone"), eq(8)))
                .thenReturn(List.of("iPhone 13 Pro", "iPhone 14 Pro Max"));

        // Act
        List<String> suggestions = productSearchService.suggest("iphone");

        // Assert
        assertThat(suggestions).hasSize(2);
        assertThat(suggestions).contains("iPhone 13 Pro", "iPhone 14 Pro Max");
    }
}
