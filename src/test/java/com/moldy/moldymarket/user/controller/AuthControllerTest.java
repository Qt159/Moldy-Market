package com.moldy.moldymarket.user.controller;

import com.moldy.moldymarket.common.exception.GlobalExceptionHandler;
import com.moldy.moldymarket.common.ratelimit.RateLimitExceededException;
import com.moldy.moldymarket.security.jwt.JwtProperties;
import com.moldy.moldymarket.user.dto.request.LoginRequest;
import com.moldy.moldymarket.user.dto.response.AuthResponse;
import com.moldy.moldymarket.user.service.AuthenticationService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {
    private static final String AUTH_PATH = "/api/v1/auth";
    private static final String AUTH_HEADER = "X-Moldy-Auth";
    private static final String COOKIE_NAME = "moldy_refresh";
    private static final String CLIENT_IP = "192.0.2.10";
    private static final LoginRequest LOGIN = new LoginRequest("user@example.test", "test-password");
    private static final String LOGIN_JSON = """
            {"email":"user@example.test","password":"test-password"}
            """;
    private static final UUID USER_ID = UUID.fromString("c45f9ff7-1061-42a7-aa15-a371e0ba7bd3");

    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties("unused", 900_000, 2_592_000_000L);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authenticationService, jwtProperties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginPassesClientIpAndReturnsRefreshTokenOnlyInCookie() throws Exception {
        when(authenticationService.login(LOGIN, CLIENT_IP))
                .thenReturn(tokens("login-access", "login-refresh"));

        mvc.perform(post(AUTH_PATH + "/login")
                        .header(AUTH_HEADER, "1")
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(COOKIE_NAME + "=login-refresh"),
                        containsString("Path=" + AUTH_PATH),
                        containsString("Max-Age=2592000"),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"))))
                .andExpect(jsonPath("$.data.accessToken").value("login-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotHaveJsonPath());

        verify(authenticationService).login(LOGIN, CLIENT_IP);
    }

    @Test
    void rateLimitedLoginReturnsRetryAfterWithoutSettingCookie() throws Exception {
        when(authenticationService.login(LOGIN, CLIENT_IP))
                .thenThrow(new RateLimitExceededException(45));

        mvc.perform(post(AUTH_PATH + "/login")
                        .header(AUTH_HEADER, "1")
                        .with(request -> {
                            request.setRemoteAddr(CLIENT_IP);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void refreshReadsCookieAndReturnsRotatedCookie() throws Exception {
        when(authenticationService.refresh("old-refresh"))
                .thenReturn(tokens("new-access", "new-refresh"));

        mvc.perform(post(AUTH_PATH + "/refresh")
                        .header(AUTH_HEADER, "1")
                        .cookie(new Cookie(COOKIE_NAME, "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE_NAME + "=new-refresh")))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotHaveJsonPath());
    }

    @Test
    void refreshRejectsMissingCookie() throws Exception {
        mvc.perform(post(AUTH_PATH + "/refresh").header(AUTH_HEADER, "1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void refreshRequiresAuthHeader() throws Exception {
        mvc.perform(post(AUTH_PATH + "/refresh").cookie(new Cookie(COOKIE_NAME, "old-refresh")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(authenticationService);
    }

    @Test
    void logoutRevokesRefreshTokenAndExpiresCookie() throws Exception {
        mvc.perform(post(AUTH_PATH + "/logout")
                        .header(AUTH_HEADER, "1")
                        .cookie(new Cookie(COOKIE_NAME, "active-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(COOKIE_NAME + "=;"),
                        containsString("Path=" + AUTH_PATH),
                        containsString("Max-Age=0"))));

        verify(authenticationService).logout("active-refresh");
    }

    @Test
    void logoutWithoutCookieStillSucceeds() throws Exception {
        mvc.perform(post(AUTH_PATH + "/logout").header(AUTH_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verifyNoInteractions(authenticationService);
    }

    private AuthResponse tokens(String accessToken, String refreshToken) {
        return new AuthResponse(USER_ID, LOGIN.email(), "Test User", accessToken, refreshToken, "Bearer");
    }
}
