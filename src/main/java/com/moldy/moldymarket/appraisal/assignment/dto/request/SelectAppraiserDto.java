package com.moldy.moldymarket.appraisal.assignment.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;
// luồng seller chọn, luồng appraiser chọn thì có Api accept invitation
public record SelectAppraiserDto(

    @NotNull(message = "Application ID is required")
    UUID applicationId) {
}