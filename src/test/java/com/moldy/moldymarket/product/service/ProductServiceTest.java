package com.moldy.moldymarket.product.service;

import com.moldy.moldymarket.category.service.CategoryService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.ratelimit.RateLimitPolicy;
import com.moldy.moldymarket.common.ratelimit.RateLimiter;
import com.moldy.moldymarket.common.storage.service.FileStorageService;
import com.moldy.moldymarket.product.dto.CreateProductRequest;
import com.moldy.moldymarket.product.dto.ProductResponse;
import com.moldy.moldymarket.product.dto.RemoveProductRequest;
import com.moldy.moldymarket.product.dto.UpdateProductRequest;
import com.moldy.moldymarket.product.entity.PricingStrategy;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductCondition;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.mapper.ProductMapper;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryService categoryService;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private StoreAccessGuard storeAccessGuard;

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ProductService productService;

    private UUID sellerId;
    private UUID categoryId;
    private UUID productId;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        productId = UUID.randomUUID();

        sampleProduct = new Product(
                sellerId, null, categoryId,
                "ThinkPad T14", "Great laptop",
                BigDecimal.valueOf(15000000), ProductCondition.LIKE_NEW,
                PricingStrategy.SELLER_SET, List.of("https://example.com/img1.jpg"), Map.of("ram", "16GB")
        );
        ReflectionTestUtils.setField(sampleProduct, "id", productId);
        ReflectionTestUtils.setField(sampleProduct, "version", 1L);
    }

    private Authentication mockAuth(UUID uid) {
        Authentication auth = mock(Authentication.class);
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(uid);
        when(auth.getPrincipal()).thenReturn(userDetails);
        return auth;
    }

    @Test
    @DisplayName("should create personal product successfully when valid")
    void should_createProduct_when_requestValid() {
        // Arrange
        Authentication auth = mockAuth(sellerId);
        CreateProductRequest request = new CreateProductRequest(
                null, categoryId, "ThinkPad T14", "Great laptop",
                BigDecimal.valueOf(15000000), ProductCondition.LIKE_NEW,
                PricingStrategy.SELLER_SET, List.of("https://example.com/img1.jpg"), Map.of()
        );

        when(productRepository.countBySellerIdAndStoreIdIsNullAndStatusIn(eq(sellerId), anySet())).thenReturn(5L);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductResponse expectedResponse = mock(ProductResponse.class);
        when(productMapper.toResponse(sampleProduct)).thenReturn(expectedResponse);

        // Act
        ProductResponse response = productService.create(auth, request);

        // Assert
        assertThat(response).isNotNull();
        verify(rateLimiter).check(RateLimitPolicy.PRODUCT_CREATE, sellerId.toString());
        verify(categoryService).validateProductCategory(categoryId);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("should throw PRICING_STRATEGY_NOT_SUPPORTED when strategy is not SELLER_SET")
    void should_throwPricingStrategyNotSupported_when_strategyNotSellerSet() {
        // Arrange
        Authentication auth = mockAuth(sellerId);
        CreateProductRequest request = new CreateProductRequest(
                null, categoryId, "ThinkPad T14", "Great laptop",
                BigDecimal.valueOf(15000000), ProductCondition.LIKE_NEW,
                PricingStrategy.AUTO_PRICING, null, null
        );

        // Act & Assert
        assertThatThrownBy(() -> productService.create(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRICING_STRATEGY_NOT_SUPPORTED);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw PRODUCT_LIMIT_REACHED when personal listings reach maximum")
    void should_throwProductLimitReached_when_personalLimitExceeded() {
        // Arrange
        Authentication auth = mockAuth(sellerId);
        CreateProductRequest request = new CreateProductRequest(
                null, categoryId, "Laptop", "desc",
                BigDecimal.valueOf(1000000), ProductCondition.USED,
                PricingStrategy.SELLER_SET, null, null
        );

        when(productRepository.countBySellerIdAndStoreIdIsNullAndStatusIn(eq(sellerId), anySet())).thenReturn(200L);

        // Act & Assert
        assertThatThrownBy(() -> productService.create(auth, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_LIMIT_REACHED);

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("should check store access when creating product for store")
    void should_checkStoreAccess_when_storeProductCreated() {
        // Arrange
        UUID storeId = UUID.randomUUID();
        Authentication auth = mockAuth(sellerId);
        CreateProductRequest request = new CreateProductRequest(
                storeId, categoryId, "Store Laptop", "desc",
                BigDecimal.valueOf(1000000), ProductCondition.USED,
                PricingStrategy.SELLER_SET, null, null
        );

        when(productRepository.countByStoreIdAndStatusIn(eq(storeId), anySet())).thenReturn(10L);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);
        when(productMapper.toResponse(sampleProduct)).thenReturn(mock(ProductResponse.class));

        // Act
        productService.create(auth, request);

        // Assert
        verify(storeAccessGuard).requireStoreAccess(storeId, sellerId, PermissionConstants.PRODUCT_CREATE);
    }

    @Test
    @DisplayName("should update product successfully when caller is owner and version matches")
    void should_updateProduct_when_callerIsOwnerAndVersionMatches() {
        // Arrange
        Authentication auth = mockAuth(sellerId);
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Title", "Updated Desc", BigDecimal.valueOf(16000000),
                ProductCondition.LIKE_NEW, null, null, 1L
        );

        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));
        when(productMapper.toResponse(sampleProduct)).thenReturn(mock(ProductResponse.class));

        // Act
        ProductResponse response = productService.update(auth, productId, request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(sampleProduct.getTitle()).isEqualTo("Updated Title");
        assertThat(sampleProduct.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(16000000));
    }

    @Test
    @DisplayName("should update allowOffer flag when provided in update request")
    void should_updateAllowOffer_when_flagProvided() {
        Authentication auth = mockAuth(sellerId);
        UpdateProductRequest request = new UpdateProductRequest(
                null, null, null, null, null, null, 1L, null, false
        );

        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));
        when(productMapper.toResponse(sampleProduct)).thenReturn(mock(ProductResponse.class));

        productService.update(auth, productId, request);

        assertThat(sampleProduct.isAllowOffer()).isFalse();
    }

    @Test
    @DisplayName("should throw CONCURRENT_MODIFICATION when version mismatch")
    void should_throwConcurrentModification_when_versionMismatch() {
        // Arrange
        Authentication auth = mockAuth(sellerId);
        UpdateProductRequest request = new UpdateProductRequest(
                "Updated Title", null, null, null, null, null, 999L
        );

        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));

        // Act & Assert
        assertThatThrownBy(() -> productService.update(auth, productId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONCURRENT_MODIFICATION);
    }

    @Test
    @DisplayName("should throw PRODUCT_ACCESS_DENIED when caller is not product owner")
    void should_throwProductAccessDenied_when_callerNotOwner() {
        // Arrange
        UUID strangerId = UUID.randomUUID();
        Authentication auth = mockAuth(strangerId);
        UpdateProductRequest request = new UpdateProductRequest(
                "Hacked Title", null, null, null, null, null, 1L
        );

        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));

        // Act & Assert
        assertThatThrownBy(() -> productService.update(auth, productId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("should change status to HIDDEN when product is ACTIVE")
    void should_changeStatus_when_productIsActive() {
        // Arrange
        Authentication auth = mockAuth(sellerId);
        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));

        // Act
        productService.changeStatus(auth, productId, ProductStatus.HIDDEN);

        // Assert
        assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    @DisplayName("should throw PRODUCT_STATUS_NOT_ALLOWED when seller sets non-settable status")
    void should_throwProductStatusNotAllowed_when_statusNotSettableBySeller() {
        // Arrange
        Authentication auth = mock(Authentication.class);

        // Act & Assert
        assertThatThrownBy(() -> productService.changeStatus(auth, productId, ProductStatus.SOLD))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_STATUS_NOT_ALLOWED);
    }

    @Test
    @DisplayName("should soft delete product by setting status to DELETED")
    void should_softDeleteProduct_when_ownerInvokes() {
        // Arrange
        Authentication auth = mockAuth(sellerId);
        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));

        // Act
        productService.softDelete(auth, productId);

        // Assert
        assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.DELETED);
    }

    @Test
    void cannotHideAProductHeldByAnUnpaidCheckout() {
        var auth = mockAuth(sellerId);
        sampleProduct.reserveForCheckout(UUID.randomUUID(), null, java.time.Instant.now());
        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));
        assertThatThrownBy(() -> productService.changeStatus(auth, productId, ProductStatus.HIDDEN))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_RESERVED);
        assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void cannotDeleteAProductHeldByAnUnpaidCheckout() {
        var auth = mockAuth(sellerId);
        sampleProduct.reserveForCheckout(UUID.randomUUID(), null, java.time.Instant.now());
        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));
        assertThatThrownBy(() -> productService.softDelete(auth, productId))
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRODUCT_RESERVED);
        assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("should remove product with admin reason and restore it")
    void should_removeAndRestoreProduct_when_adminInvoked() {
        // Arrange
        UUID adminId = UUID.randomUUID();
        Authentication auth = mockAuth(adminId);
        when(productRepository.findByIdAndStatusNot(productId, ProductStatus.DELETED))
                .thenReturn(Optional.of(sampleProduct));
        when(productMapper.toResponse(sampleProduct)).thenReturn(mock(ProductResponse.class));

        // Act: Remove
        productService.removeProduct(auth, productId, new RemoveProductRequest("Violates policy"));

        // Assert removed
        assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.REMOVED);
        assertThat(sampleProduct.getRemovalReason()).isEqualTo("Violates policy");

        // Act: Restore
        productService.restoreProduct(productId);

        // Assert restored
        assertThat(sampleProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(sampleProduct.getRemovalReason()).isNull();
    }
}
