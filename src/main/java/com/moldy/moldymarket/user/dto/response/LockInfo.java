package com.moldy.moldymarket.user.dto.response;

import java.time.Instant;

public record LockInfo(
        String reason,
        Instant lockedAt,
        ActorRef lockedBy)
{}
