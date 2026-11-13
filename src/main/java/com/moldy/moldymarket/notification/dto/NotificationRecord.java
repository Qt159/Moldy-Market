package com.moldy.moldymarket.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationRecord {
    private String id;
    private String userId;
    private String type;
    private String title;
    private String message;
    private String relatedEntityId;
    private String relatedEntityType;
    private boolean read;
    private boolean deleted;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
