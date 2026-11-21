
package com.moldy.moldymarket.wishlist.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@Schema(description = "Wishlist item response")
public class WishlistItemResponse {

    @Schema(description = "Wishlist item ID")
    private UUID id;

    @Schema(description = "Product ID")
    private UUID productId;

    @Schema(description = "Timestamp when added to wishlist")
    private Instant createdAt;
}
