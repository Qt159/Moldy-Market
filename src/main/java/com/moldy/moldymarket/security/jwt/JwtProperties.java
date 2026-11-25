package com.moldy.moldymarket.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties (
    @NotBlank @Size(min = 32) String secret,
    long accessTokenExpiration,
    long refreshTokenExpiration
) {
}
