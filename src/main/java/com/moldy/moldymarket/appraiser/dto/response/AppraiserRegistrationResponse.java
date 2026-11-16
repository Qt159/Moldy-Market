package com.moldy.moldymarket.appraiser.dto.response;

import com.moldy.moldymarket.appraiser.enums.AppraiserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AppraiserRegistrationResponse {

    private UUID id;
    private AppraiserStatus status;
    private Instant createdAt;
}