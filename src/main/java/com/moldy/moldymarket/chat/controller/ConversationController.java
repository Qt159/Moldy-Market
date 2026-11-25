package com.moldy.moldymarket.chat.controller;

import com.moldy.moldymarket.chat.dto.request.StartConversationRequest;
import com.moldy.moldymarket.chat.dto.request.MarkReadRequest;
import com.moldy.moldymarket.chat.dto.response.ConversationResponse;
import com.moldy.moldymarket.chat.dto.response.MessageResponse;
import com.moldy.moldymarket.chat.service.ConversationService;
import com.moldy.moldymarket.common.response.ApiResponse;
import com.moldy.moldymarket.common.exception.AppException;
import com.moldy.moldymarket.common.exception.ErrorCode;
import com.moldy.moldymarket.security.PermissionConstants;
import com.moldy.moldymarket.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/conversations")
@PreAuthorize("hasAuthority('" + PermissionConstants.CHAT_SEND + "')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConversationController {
    ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> startConversation(@AuthenticationPrincipal CustomUserDetails user,
                                                                               @Valid @RequestBody StartConversationRequest request){
        return ResponseEntity.ok(ApiResponse.success(conversationService.startOrGetConversation(user.getUserId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getUserConversationList(@AuthenticationPrincipal CustomUserDetails user){
        return ResponseEntity.ok(ApiResponse.success(conversationService.getUserConversationList(user.getUserId())));
    }

    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> getMessage(@AuthenticationPrincipal CustomUserDetails user,
                                                            @PathVariable UUID conversationId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "4") int size){
        //load messages from newest to oldest
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("sentAt").descending().and(Sort.by("id").descending()));

        return ResponseEntity.ok(ApiResponse.success(conversationService.getChatHistory(user.getUserId(), conversationId, pageRequest)));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody MarkReadRequest request){
        conversationService.markAsRead(id, user.getUserId(), request.messageId());
        return ResponseEntity.noContent().build();
    }

}
