package com.moldy.moldymarket.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "admin")
public record AdminProperties(
        String email,
        String password,
        String fullName
) {
}
