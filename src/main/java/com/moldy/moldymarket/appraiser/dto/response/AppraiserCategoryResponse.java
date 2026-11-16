
package com.moldy.moldymarket.appraiser.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AppraiserCategoryResponse {

    private UUID categoryId;
    private String categoryName;
    private boolean verified;
}
