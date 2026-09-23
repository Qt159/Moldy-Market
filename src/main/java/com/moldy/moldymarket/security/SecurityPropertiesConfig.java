package com.moldy.moldymarket.security;

import com.moldy.moldymarket.security.jwt.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityPropertiesConfig {
}
