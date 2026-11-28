package com.moldy.moldymarket.user.dto.response;

import java.util.UUID;

public record ActorRef(UUID id, String fullName, String email) {}
