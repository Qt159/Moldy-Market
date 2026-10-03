package com.moldy.moldymarket.notification.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Base64;
import java.util.Map;

public class NotificationPaginationToken {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NotificationPaginationToken() {}

    /**
     * Encode LastEvaluatedKey + userId thành Base64 cursor token.
     * Trả về null nếu không còn trang tiếp theo.
     */
    public static String encode(Map<String, AttributeValue> lastEvaluatedKey, String userId) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) return null;

        try {
            Map<String, String> tokenMap = Map.of(
                    "userId", userId,
                    "PK", lastEvaluatedKey.get("PK").s(),
                    "SK", lastEvaluatedKey.get("SK").s()
            );
            String json = MAPPER.writeValueAsString(tokenMap);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode pagination token", e);
        }
    }

    /**
     * Decode Base64 cursor token thành ExclusiveStartKey cho DynamoDB query.
     * Validate userId khớp để ngăn dùng token của người khác.
     * Trả về null nếu token null (trang đầu tiên).
     *
     * @throws AppException PAGINATION_TOKEN_USER_MISMATCH nếu token thuộc user khác
     * @throws AppException INVALID_PAGINATION_TOKEN nếu token sai format
     */
    public static Map<String, AttributeValue> decode(String token, String userId) {
        if (token == null || token.isBlank()) return null;

        try {
            String json = new String(Base64.getUrlDecoder().decode(token));
            Map<?, ?> tokenMap = MAPPER.readValue(json, Map.class);

            String tokenUserId = (String) tokenMap.get("userId");
            if (!userId.equals(tokenUserId)) {
                throw new AppException(ErrorCode.PAGINATION_TOKEN_USER_MISMATCH);
            }

            return Map.of(
                    "PK", AttributeValue.fromS((String) tokenMap.get("PK")),
                    "SK", AttributeValue.fromS((String) tokenMap.get("SK"))
            );

        } catch (AppException e) {
            throw e;  // re-throw AppException, không wrap lại
        } catch (Exception e) {
            throw new AppException(ErrorCode.INVALID_PAGINATION_TOKEN);
        }
    }
}
