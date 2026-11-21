
package com.moldy.moldymarket.wishlist.service;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.product.repository.ProductRepository;
import com.moldy.moldymarket.wishlist.dto.WishlistItemResponse;
import com.moldy.moldymarket.wishlist.entity.WishlistItem;
import com.moldy.moldymarket.wishlist.mapper.WishlistMapper;
import com.moldy.moldymarket.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final ProductRepository productRepository;
    private final WishlistMapper wishlistMapper;

    @Transactional
    public WishlistItemResponse addToWishlist(UUID userId, UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new AppException(ErrorCode.PRODUCT_ALREADY_WISHLISTED);
        }

        WishlistItem wishlistItem = WishlistItem.create(userId, productId);
        WishlistItem savedItem = wishlistRepository.save(wishlistItem);

        return wishlistMapper.toResponse(savedItem);
    }

    @Transactional
    public void removeFromWishlist(UUID userId, UUID productId) {

        int deletedRows = wishlistRepository.deleteByUserIdAndProductId(userId, productId);

        if (deletedRows == 0) {
            throw new AppException(ErrorCode.WISHLIST_ITEM_NOT_FOUND);
        }
    }

    public Page<WishlistItemResponse> getWishlist( UUID userId, Pageable pageable) {
        return wishlistRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable)
                .map(wishlistMapper::toResponse);
    }

    public boolean isProductWishlisted(UUID userId, UUID productId) {
        return wishlistRepository.existsByUserIdAndProductId(userId, productId);
    }

    public long getWishlistCount(UUID userId) {
        return wishlistRepository.countByUserId(userId);
    }

    public long getProductWishlistCount(UUID productId) {
        return wishlistRepository.countByProductId(productId);
    }

    @Transactional
    public void clearWishlist(UUID userId) {
        wishlistRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public void removeProductFromAllWishlists(UUID productId) {
        wishlistRepository.deleteAllByProductId(productId);
    }

    public boolean hasWishlistItems(UUID userId) {
        return wishlistRepository.existsByUserId(userId);
    }

    public List<UUID> getWishlistedProductIds(UUID userId) {
        return wishlistRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(WishlistItem::getProductId)
                .toList();
    }
}
