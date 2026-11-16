package com.moldy.moldymarket.notification;

import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.notification.dto.NotificationPaginationToken;
import com.moldy.moldymarket.notification.repository.NotificationRepository;
import com.moldy.moldymarket.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationIntegrationTest {

    @Test
    void mapsLambdaRecordsAndReturnsTheCursorForTheCurrentUser() {
        var client = mock(DynamoDbClient.class);
        var service = new NotificationService(new NotificationRepository(client, "notifications-test"));
        var startKey = Map.of(
                "PK", AttributeValue.fromS("USER#buyer"),
                "SK", AttributeValue.fromS("NOTIFICATION#2026-11-16T00:00:00Z#notice"));
        when(client.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder()
                .items(Map.of(
                        "notificationId", AttributeValue.fromS("notice"),
                        "content", AttributeValue.fromS("Order updated"),
                        "createdAt", AttributeValue.fromS("2026-11-16T00:00:00Z")))
                .lastEvaluatedKey(startKey)
                .build());

        var page = service.getPage("buyer", 10, null);
        var request = ArgumentCaptor.forClass(QueryRequest.class);
        verify(client).query(request.capture());
        assertEquals("USER#buyer", request.getValue().expressionAttributeValues().get(":pk").s());
        assertEquals("notice", page.getItems().get(0).getId());
        assertEquals("Order updated", page.getItems().get(0).getMessage());
        assertTrue(page.isHasMore());
        assertEquals(startKey, NotificationPaginationToken.decode(page.getNextToken(), "buyer"));
        assertEquals(ErrorCode.PAGINATION_TOKEN_USER_MISMATCH, assertThrows(AppException.class,
                () -> NotificationPaginationToken.decode(page.getNextToken(), "other")).getErrorCode());
    }

    @Test
    void refusesACursorWithAnAlteredPartitionKey() {
        var cursor = NotificationPaginationToken.encode(Map.of(
                "PK", AttributeValue.fromS("USER#other"),
                "SK", AttributeValue.fromS("NOTIFICATION#forward")), "buyer");
        assertEquals(ErrorCode.PAGINATION_TOKEN_USER_MISMATCH, assertThrows(AppException.class,
                () -> NotificationPaginationToken.decode(cursor, "buyer")).getErrorCode());
    }
}
