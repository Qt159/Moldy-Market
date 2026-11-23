package com.moldy.moldymarket.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MarkReadRequest(@NotNull @Positive Long messageId) {}
