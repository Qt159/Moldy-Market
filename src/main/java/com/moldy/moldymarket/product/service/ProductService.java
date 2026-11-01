package com.moldy.moldymarket.product.service;

import com.moldy.moldymarket.category.service.CategoryService;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.common.ratelimit.RateLimitPolicy;
import com.moldy.moldymarket.common.ratelimit.InMemoryRateLimiter;
import com.moldy.moldymarket.common.ratelimit.RateLimiter;
import com.moldy.moldymarket.common.response.PagedResponse;
import com.moldy.moldymarket.product.dto.*;
import com.moldy.moldymarket.product.entity.PricingStrategy;
import com.moldy.moldymarket.product.entity.Product;
import com.moldy.moldymarket.product.entity.ProductStatus;
import com.moldy.moldymarket.product.mapper.ProductMapper;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.product.repository.ProductSpecifications;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import com.moldy.moldymarket.store.service.StoreAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductMapper productMapper;
    private final StoreAccessGuard storeAccessGuard;
    private final RateLimiter rateLimiter;
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "price", "title");

    private static final Set<ProductStatus> SELLER_SETTABLE = Set.of(
            ProductStatus.ACTIVE, ProductStatus.HIDDEN
    );

    private static final Set<ProductStatus> COUNTED_STATUSES = Set.of(ProductStatus.ACTIVE, ProductStatus.HIDDEN);
    private static final int MAX_LISTINGS_PERSONAL = 200;
    private static final int MAX_LISTINGS_PER_STORE = 1000;

    private void enforceCreateLimits(UUID sellerId, UUID storeId) {
        if (storeId != null) {
            long total = productRepository.countByStoreIdAndStatusIn(storeId, COUNTED_STATUSES);
            if (total >= MAX_LISTINGS_PER_STORE) {
                throw new AppException(ErrorCode.PRODUCT_LIMIT_REACHED,
                        "Store has reached the limit of " + MAX_LISTINGS_PER_STORE);
            }
        } else {
            long total = productRepository.countBySellerIdAndStoreIdIsNullAndStatusIn(sellerId, COUNTED_STATUSES);
            if (total >= MAX_LISTINGS_PERSONAL) {
                throw new AppException(ErrorCode.PRODUCT_LIMIT_REACHED,
                        "You have reached the limit of " + MAX_LISTINGS_PERSONAL + " listings");
            }
        }

    }

    @Transactional
    public ProductResponse create(Authentication authentication, CreateProductRequest request) {
        UUID userId = extractUserId(authentication);

        // Chặn spam
        rateLimiter.check(RateLimitPolicy.PRODUCT_CREATE, userId.toString());

        // MVP: chỉ nhánh seller tự set giá. Auto-Pricing (SPE-UC01)/Appraiser (AP-UC02) làm đợt sau.
        if (request.pricingStrategy() != PricingStrategy.SELLER_SET) {
            throw new AppException(ErrorCode.PRICING_STRATEGY_NOT_SUPPORTED);
        }

        categoryService.validateProductCategory(request.categoryId());

        if (request.storeId() != null) {
            storeAccessGuard.requireStoreAccess(request.storeId(), userId, PermissionConstants.PRODUCT_CREATE);
        }

        enforceCreateLimits(userId, request.storeId());

        Product product = new Product(userId, request.storeId(), request.categoryId(),
                request.title(), request.description(), request.price(), request.condition(),
                request.pricingStrategy(), request.images(), request.attributes());

        return productMapper.toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Authentication authentication, UUID id, UpdateProductRequest request) {
        Product product = findActiveOrThrow(id);
        requireOwnership(authentication, product);
        requiredEditable(product);

        if (!product.getVersion().equals(request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        product.update(request.title(), request.description(), request.price(),
                request.condition(), request.images(), request.attributes());

        return productMapper.toResponse(product);
    }

    @Transactional
    public void changeStatus(Authentication authentication, UUID id, ProductStatus newStatus) {
        if (!SELLER_SETTABLE.contains(newStatus)) {
            throw new AppException(ErrorCode.PRODUCT_STATUS_NOT_ALLOWED);
        }

        Product product = findActiveOrThrow(id);
        requireOwnership(authentication, product);
        requireNotRemoved(product);

        // Trạng thái hiện tại không nằm trong danh sách (ví dụ SOLD): không cho đổi
        if (!SELLER_SETTABLE.contains(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_STATUS_LOCKED);
        }

        // TODO: Khi có order module, chặn đổi trạng thái nếu SP đang trong đơn chưa hoàn tất
        product.changeStatus(newStatus);
    }

    @Transactional
    public void softDelete(Authentication authentication, UUID id) {
        Product product = findActiveOrThrow(id);
        requireOwnership(authentication, product);
        requiredEditable(product);
        product.changeStatus(ProductStatus.DELETED);
    }

    public PagedResponse<ProductResponse> search(ProductFilterRequest filter, Pageable pageable) {
        validateSort(pageable);
        Specification<Product> spec = Specification.where(ProductSpecifications.statusActive())
                .and(ProductSpecifications.sellerAndStoreVisible())
                .and(ProductSpecifications.keyword(filter.keyword()))
                .and(ProductSpecifications.categoryId(filter.categoryId()))
                .and(ProductSpecifications.priceBetween(filter.minPrice(), filter.maxPrice()))
                .and(ProductSpecifications.condition(filter.condition()));

        Page<Product> page = productRepository.findAll(spec, pageable);
        List<ProductResponse> content = page.map(productMapper::toPublicResponse).getContent();

        return PagedResponse.of(page, content, pageable);
    }

    // getById: chủ sở hữu và người kiểm duyệt xem đầy đủ, người khác xem bản công khai
    public ProductResponse getById(Authentication authentication, UUID id) {
        Product product = findActiveOrThrow(id);

        //  Sản phẩm không ACTIVE: chỉ người sở hữu mới xem được
        boolean privileged = isOwnerSide(authentication, product)
                || hasAuthority(authentication, PermissionConstants.PRODUCT_MODERATE);

        if (!privileged && !isPubliclyVisible(id))
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);

        return privileged ? productMapper.toResponse(product) : productMapper.toPublicResponse(product);
    }

    @Transactional
    public ProductResponse removeProduct(Authentication authentication, UUID id, RemoveProductRequest request) {
        Product product = findActiveOrThrow(id);
        if (product.getStatus() == ProductStatus.REMOVED) {
            throw new AppException(ErrorCode.PRODUCT_ALREADY_REMOVED);
        }
        product.remove(extractUserId(authentication), request.reason());
        return productMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse restoreProduct(UUID id) {
        Product product = findActiveOrThrow(id);
        if (product.getStatus() != ProductStatus.REMOVED) {
            throw new AppException(ErrorCode.PRODUCT_NOT_REMOVED);
        }
        product.restore();
        return productMapper.toResponse(product);
    }

    // -- Helpers ---------------------------------

    private boolean isOwnerSide(Authentication authentication, Product product) {
        // Chưa đăng nhập: authentication là null hoặc không phải CustomUserDetails
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            return false;
        }

        UUID userId = details.getUserId();

        if (product.belongsToStore()) {
            return storeAccessGuard.isStoreMember(product.getStoreId(), userId);
        }

        return product.getSellerId().equals(userId);
    }

    private Product findActiveOrThrow(UUID id) {
        return productRepository.findByIdAndStatusNot(id, ProductStatus.DELETED)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private void requireOwnership(Authentication authentication, Product product) {
        UUID userId = extractUserId(authentication);
        if (product.belongsToStore()) {
            storeAccessGuard.requireStoreAccess(product.getStoreId(), userId, PermissionConstants.PRODUCT_MANAGE_OWN);
        } else if (!product.getSellerId().equals(userId)) {
            throw new AppException(ErrorCode.PRODUCT_ACCESS_DENIED);
        }
    }

    private UUID extractUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return details.getUserId();
    }

    private boolean isPubliclyVisible(UUID id) {
        return productRepository.exists(
                Specification.where(ProductSpecifications.hasId(id))
                        .and(ProductSpecifications.statusActive())
                        .and(ProductSpecifications.sellerAndStoreVisible()));
    }

    private void requireNotRemoved(Product product) {
        if (product.getStatus() == ProductStatus.REMOVED) {
            throw new AppException(ErrorCode.PRODUCT_REMOVED);
        }
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }

    // Seller chỉ được sửa bài đang ACTIVE hoặc HIDDEN
    private void requiredEditable(Product product) {
        requireNotRemoved(product);
        if (!SELLER_SETTABLE.contains(product.getStatus())) {
            throw new AppException(ErrorCode.PRODUCT_STATUS_LOCKED);
        }
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTABLE_FIELDS.contains(order.getProperty())) {
                throw new AppException(ErrorCode.INVALID_REQUEST,
                        "Unsupported sort field. Allowed: createdAt, price, title");
            }
        }
    }

}
