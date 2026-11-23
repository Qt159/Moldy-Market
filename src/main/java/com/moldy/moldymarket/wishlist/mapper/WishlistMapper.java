
package com.moldy.moldymarket.wishlist.mapper;

import com.moldy.moldymarket.wishlist.dto.WishlistItemResponse;
import com.moldy.moldymarket.wishlist.entity.WishlistItem;
import org.springframework.stereotype.Component;

@Component
public class WishlistMapper {

    public WishlistItemResponse toResponse(WishlistItem wishlistItem) {
        if (wishlistItem == null) {
            return null;
        }

        return WishlistItemResponse.builder()
                .id(wishlistItem.getId())
                .productId(wishlistItem.getProductId())
                .createdAt(wishlistItem.getCreatedAt())
                .build();
    }
}
