package com.moldy.lambda.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.moldy.shared.notification.NotificationEvent;
import com.moldy.shared.notification.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrevoEmailServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private ObjectMapper objectMapper;
    private BrevoEmailService service;
    private NotificationEvent sampleEvent;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new BrevoEmailService(
                httpClient,
                objectMapper,
                "test-brevo-api-key",
                "verified-sender@moldymarket.com",
                "Moldy Market",
                URI.create("https://api.brevo.com/v3/smtp/email"),
                Duration.ofSeconds(10)
        );

        sampleEvent = new NotificationEvent(
                "noti-123",
                "user-456",
                NotificationType.ORDER_COMPLETED,
                "ORDER",
                "ord-789",
                "Đơn hàng hoàn tất",
                "Đơn hàng của bạn đã hoàn tất thành công.",
                Instant.now(),
                "buyer@example.com"
        );
    }

    @Test
    @DisplayName("Successful API response (201 Created) accepts the email")
    void successfulApiResponseAcceptsEmail() throws Exception {
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"messageId\":\"<20261124.test@smtp-relay.brevo.com>\"}");
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        service.sendEmail(sampleEvent);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());

        HttpRequest sentRequest = requestCaptor.getValue();
        assertThat(sentRequest.uri()).isEqualTo(URI.create("https://api.brevo.com/v3/smtp/email"));
        assertThat(sentRequest.method()).isEqualTo("POST");
        assertThat(sentRequest.headers().firstValue("api-key")).contains("test-brevo-api-key");
        assertThat(sentRequest.headers().firstValue("accept")).contains("application/json");
        assertThat(sentRequest.headers().firstValue("content-type")).contains("application/json");
    }

    @Test
    @DisplayName("Missing recipient throws PermanentDeliveryException")
    void missingRecipientThrowsPermanentDeliveryException() {
        NotificationEvent eventWithoutRecipient = sampleEvent.withRecipientEmail(null);

        assertThatThrownBy(() -> service.sendEmail(eventWithoutRecipient))
                .isInstanceOf(PermanentDeliveryException.class)
                .hasMessageContaining("recipient email");

        NotificationEvent eventWithBlankRecipient = sampleEvent.withRecipientEmail("   ");

        assertThatThrownBy(() -> service.sendEmail(eventWithBlankRecipient))
                .isInstanceOf(PermanentDeliveryException.class)
                .hasMessageContaining("recipient email");

        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Missing API key throws PermanentDeliveryException")
    void missingApiKeyThrowsPermanentDeliveryException() {
        BrevoEmailService serviceWithoutKey = new BrevoEmailService(
                httpClient,
                objectMapper,
                "",
                "sender@moldymarket.com",
                "Moldy Market",
                URI.create("https://api.brevo.com/v3/smtp/email"),
                Duration.ofSeconds(10)
        );

        assertThatThrownBy(() -> serviceWithoutKey.sendEmail(sampleEvent))
                .isInstanceOf(PermanentDeliveryException.class)
                .hasMessageContaining("API key");

        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Missing sender configuration throws PermanentDeliveryException")
    void missingSenderConfigurationThrowsPermanentDeliveryException() {
        BrevoEmailService serviceWithoutSender = new BrevoEmailService(
                httpClient,
                objectMapper,
                "some-key",
                "   ",
                "Moldy Market",
                URI.create("https://api.brevo.com/v3/smtp/email"),
                Duration.ofSeconds(10)
        );

        assertThatThrownBy(() -> serviceWithoutSender.sendEmail(sampleEvent))
                .isInstanceOf(PermanentDeliveryException.class)
                .hasMessageContaining("sender email");

        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("HTTP 400 Bad Request throws PermanentDeliveryException")
    void http400ThrowsPermanentDeliveryException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(400);
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(PermanentDeliveryException.class)
                .satisfies(ex -> {
                    PermanentDeliveryException pde = (PermanentDeliveryException) ex;
                    assertThat(pde.statusCode()).isEqualTo(400);
                });
    }

    @Test
    @DisplayName("HTTP 401 Unauthorized throws PermanentDeliveryException")
    void http401ThrowsPermanentDeliveryException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(401);
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(PermanentDeliveryException.class)
                .satisfies(ex -> {
                    PermanentDeliveryException pde = (PermanentDeliveryException) ex;
                    assertThat(pde.statusCode()).isEqualTo(401);
                });
    }

    @Test
    @DisplayName("HTTP 429 Rate Limit throws RetryableException")
    void http429ThrowsRetryableException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("429");
    }

    @Test
    @DisplayName("HTTP 500 Internal Server Error throws RetryableException")
    void http500ThrowsRetryableException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(500);
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("Network timeout throws RetryableException")
    void networkTimeoutThrowsRetryableException() throws Exception {
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenThrow(new HttpTimeoutException("Request timed out"));

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("network or timeout error");
    }

    @Test
    @DisplayName("Network IOException throws RetryableException")
    void networkIOExceptionThrowsRetryableException() throws Exception {
        when(httpClient.<String>send(any(HttpRequest.class), any()))
                .thenThrow(new IOException("Connection reset by peer"));

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("network or timeout error");
    }

    @Test
    @DisplayName("Malformed provider response (non-JSON) throws RetryableException")
    void malformedProviderResponseThrowsRetryableException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("<html><head><title>502 Bad Gateway</title></head></html>");
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    @DisplayName("Unexpected provider response structure (missing messageId) throws RetryableException")
    void unexpectedProviderResponseStructureThrowsRetryableException() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"status\":\"ok\"}");
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(RetryableException.class)
                .hasMessageContaining("unexpected response structure");
    }

    @Test
    @DisplayName("Provider response bodies are not exposed in exceptions")
    void doesNotExposeProviderResponseBody() throws Exception {
        String sensitiveBody = "{\"debug\":\"private-provider-detail\"}";
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(sensitiveBody);
        when(httpClient.<String>send(any(HttpRequest.class), any())).thenReturn(httpResponse);

        assertThatThrownBy(() -> service.sendEmail(sampleEvent))
                .isInstanceOf(RetryableException.class)
                .hasMessageNotContaining("private-provider-detail");
    }
}

