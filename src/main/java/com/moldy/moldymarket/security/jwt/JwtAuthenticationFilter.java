package com.moldy.moldymarket.security.jwt;

import com.moldy.moldymarket.security.user.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHead = request.getHeader(
                HttpHeaders.AUTHORIZATION
        );

        if (authorizationHead == null || !authorizationHead.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorizationHead.substring(7);

        try {
            UUID userId = jwtService.extractUserId(token);

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserById(userId);

                boolean tokenValid = jwtService.isTokenValid(token);
                logger.info("tokenValid=" + tokenValid
                        + " enabled=" + userDetails.isEnabled()
                        + " nonLocked=" + userDetails.isAccountNonLocked());

                if (tokenValid && userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }
        } catch (JwtException | IllegalArgumentException exception) {
            logger.warn("JWT invalid: " + exception.getMessage());
            SecurityContextHolder.clearContext();
        } catch (RuntimeException exception) {
            logger.error("Auth filter failed", exception);
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
