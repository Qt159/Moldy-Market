package com.moldy.moldymarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security config tạm dùng cho staging test notification module.
 * Chỉ active khi profile = "staging-notification".
 *
 * TODO: Xóa hoặc disable khi auth module hoàn thiện.
 */
@Configuration
@EnableWebSecurity
@Profile("staging-notification")
public class StagingSecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
