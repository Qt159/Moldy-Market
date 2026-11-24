package com.moldy.lambda.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moldy.shared.notification.NotificationEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class BrevoEmailService {
    static final URI SEND_EMAIL_URI = URI.create("https://api.brevo.com/v3/smtp/email");
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;
    private final URI endpoint;
    private final Duration requestTimeout;

    public BrevoEmailService(
            ObjectMapper objectMapper,
            String apiKey,
            String senderEmail,
            String senderName) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), objectMapper,
                apiKey, senderEmail, senderName, SEND_EMAIL_URI, REQUEST_TIMEOUT);
    }

    BrevoEmailService(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String apiKey,
            String senderEmail,
            String senderName,
            URI endpoint,
            Duration requestTimeout) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = trimToEmpty(apiKey);
        this.senderEmail = trimToEmpty(senderEmail);
        this.senderName = senderName == null || senderName.isBlank()
                ? "Moldy Market" : senderName.trim();
        this.endpoint = endpoint != null ? endpoint : SEND_EMAIL_URI;
        this.requestTimeout = requestTimeout != null ? requestTimeout : REQUEST_TIMEOUT;
    }

    public void sendEmail(NotificationEvent event) {
        if (event == null || event.recipientEmail() == null || event.recipientEmail().isBlank()) {
            throw new PermanentDeliveryException("Notification event has no recipient email");
        }
        if (apiKey.isBlank()) {
            throw new PermanentDeliveryException("Brevo API key is not configured");
        }
        if (senderEmail.isBlank()) {
            throw new PermanentDeliveryException("Brevo sender email is not configured");
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of("email", senderEmail, "name", senderName),
                "to", List.of(Map.of("email", event.recipientEmail())),
                "subject", event.title() == null ? "" : event.title(),
                "textContent", event.content() == null ? "" : event.content());

        final String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new PermanentDeliveryException("Could not serialize Brevo email request", e);
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("api-key", apiKey)
                .header("accept", "application/json")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RetryableException("Brevo request failed due to a network or timeout error", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RetryableException("Brevo request was interrupted", e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            validateSuccessResponse(response.body());
            return;
        }
        if (status == 408 || status == 429 || status >= 500) {
            throw new RetryableException("Brevo returned retryable HTTP status " + status,
                    new IOException("Brevo HTTP " + status));
        }
        throw new PermanentDeliveryException("Brevo rejected the email with HTTP status " + status, status);
    }

    private void validateSuccessResponse(String body) {
        if (body == null || body.isBlank()) {
            throw new RetryableException("Brevo returned an empty response body",
                    new IOException("Empty Brevo response"));
        }
        try {
            var node = objectMapper.readTree(body);
            if (!node.isObject() || (!node.hasNonNull("messageId") && !node.hasNonNull("messageIds"))) {
                throw new RetryableException("Brevo returned an unexpected response structure",
                        new IOException("Unexpected Brevo response structure"));
            }
        } catch (JsonProcessingException e) {
            throw new RetryableException("Brevo response is not valid JSON", e);
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
