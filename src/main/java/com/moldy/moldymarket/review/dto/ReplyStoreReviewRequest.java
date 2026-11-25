package com.moldy.moldymarket.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyStoreReviewRequest(
        @NotBlank(message = "Reply content is required")
        @Size(max = 1000, message = "Reply must not exceed 1000 characters")
        String reply
) {
}
